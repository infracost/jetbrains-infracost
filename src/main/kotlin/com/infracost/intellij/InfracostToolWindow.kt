package com.infracost.intellij

import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

data class LoginResult(
    val verificationUri: String,
    val verificationUriComplete: String,
    val userCode: String,
)

class InfracostToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = try {
            val browser = JBCefBrowser()
            browser.loadHTML(InfracostResourceHtml.renderEmpty())
            InfracostToolWindowPanel(project, browser)
        } catch (_: Exception) {
            InfracostToolWindowPanel(project, null)
        }

        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)

        project.putUserData(PANEL_KEY, panel)

        project.messageBus.connect().subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(tw: ToolWindow) {
                    if (tw.id == ID) {
                        panel.checkAuthStatus()
                    }
                }
            }
        )

        panel.checkAuthStatus()
    }

    companion object {
        const val ID = "Infracost"
        val PANEL_KEY = com.intellij.openapi.util.Key.create<InfracostToolWindowPanel>("infracost.panel")

        fun show(project: Project, params: ResourceDetailsParams, data: ResourceDetailsResult) {
            ApplicationManager.getApplication().invokeLater {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return@invokeLater
                toolWindow.show()
                val panel = project.getUserData(PANEL_KEY) ?: return@invokeLater
                panel.update(params, data)
            }
        }
    }
}

class InfracostToolWindowPanel(
    private val project: Project,
    private val browser: JBCefBrowser?,
) : JPanel(BorderLayout()) {

    private val fallbackLabel: JLabel? = if (browser == null) {
        JLabel("JCEF is not available", SwingConstants.CENTER)
    } else null

    @Suppress("DEPRECATION")
    private val loginQuery: JBCefJSQuery? = browser?.let { b ->
        JBCefJSQuery.create(b).also { query ->
            query.addHandler { _ ->
                performLogin()
                null
            }
        }
    }

    init {
        if (browser != null) {
            browser.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    b: CefBrowser?, frame: CefFrame?, request: CefRequest?,
                    userGesture: Boolean, isRedirect: Boolean
                ): Boolean {
                    val url = request?.url ?: return false
                    // loadHTML uses about:blank or a data: scheme — let those through
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        BrowserUtil.browse(url)
                        return true // cancel the navigation
                    }
                    return false
                }
            }, browser.cefBrowser)
            add(browser.component, BorderLayout.CENTER)
        } else {
            add(fallbackLabel!!, BorderLayout.CENTER)
        }
    }

    private var lastParams: ResourceDetailsParams? = null
    private var lastHtml: String? = null

    /** Must be called on the EDT. */
    fun update(params: ResourceDetailsParams, data: ResourceDetailsResult) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        lastParams = params
        if (data.needsLogin == true) {
            showLogin()
            return
        }

        val html = InfracostResourceHtml.renderResult(data)
        if (html == lastHtml) return
        lastHtml = html

        if (browser != null) {
            browser.loadHTML(html)
        } else {
            fallbackLabel?.text = data.resource?.name ?: "No resource selected"
        }
    }

    fun refreshCurrentResource() {
        val params = lastParams ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val servers = LspServerManager.getInstance(project)
                    .getServersForProvider(InfracostLspServerSupportProvider::class.java)
                val server = servers.firstOrNull() ?: return@executeOnPooledThread
                val response = server.sendRequestSync {
                    (it as InfracostLanguageServer).resourceDetails(params)
                }
                val gson = Gson()
                val result = gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) update(params, result)
                }
            } catch (e: Exception) {
                LOG.debug("Failed to refresh current resource", e)
            }
        }
    }

    private fun showLogin() {
        if (browser == null || loginQuery == null) return

        val html = InfracostResourceHtml.renderLogin(loginQuery.inject("''"))
        browser.loadHTML(html)
    }

    fun checkAuthStatus(retriesLeft: Int = 3) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val servers = LspServerManager.getInstance(project)
                    .getServersForProvider(InfracostLspServerSupportProvider::class.java)
                val server = servers.firstOrNull()
                if (server == null) {
                    retryCheckAuthStatus(retriesLeft)
                    return@executeOnPooledThread
                }
                val response = server.sendRequestSync {
                    (it as InfracostLanguageServer).resourceDetails(ResourceDetailsParams("", 0))
                }
                val gson = Gson()
                val result = gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)

                if (result.needsLogin == true) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) showLogin()
                    }
                }
            } catch (_: Exception) {
                retryCheckAuthStatus(retriesLeft)
            }
        }
    }

    private fun retryCheckAuthStatus(retriesLeft: Int) {
        if (retriesLeft <= 0 || project.isDisposed) return
        Thread.sleep(5000)
        checkAuthStatus(retriesLeft - 1)
    }

    private fun performLogin() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val servers = LspServerManager.getInstance(project)
                    .getServersForProvider(InfracostLspServerSupportProvider::class.java)
                val server = servers.firstOrNull() ?: return@executeOnPooledThread
                val response = server.sendRequestSync {
                    (it as InfracostLanguageServer).login()
                }
                val gson = Gson()
                val result = gson.fromJson(gson.toJson(response), LoginResult::class.java)

                BrowserUtil.browse(result.verificationUriComplete)

                ApplicationManager.getApplication().invokeLater {
                    browser?.loadHTML(InfracostResourceHtml.renderEmpty())
                }
            } catch (e: Exception) {
                LOG.warn("Infracost login failed", e)
                ApplicationManager.getApplication().invokeLater {
                    browser?.loadHTML(InfracostResourceHtml.renderPage(
                        """<div class="state"><p>Login failed. Please try again.</p></div>"""
                    ))
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance(InfracostToolWindowPanel::class.java)
    }
}
