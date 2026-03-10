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
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.BorderLayout
import java.util.concurrent.TimeUnit
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
    }

    companion object {
        const val ID = "Infracost"
        val PANEL_KEY = com.intellij.openapi.util.Key.create<InfracostToolWindowPanel>("infracost.panel")

        fun show(project: Project, data: ResourceDetailsResult) {
            ApplicationManager.getApplication().invokeLater {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return@invokeLater
                toolWindow.show()
                val panel = project.getUserData(PANEL_KEY) ?: return@invokeLater
                panel.update(data)
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
            add(browser.component, BorderLayout.CENTER)
        } else {
            add(fallbackLabel!!, BorderLayout.CENTER)
        }
    }

    fun update(data: ResourceDetailsResult) {
        if (data.needsLogin == true) {
            showLogin()
            return
        }

        val html = InfracostResourceHtml.renderResult(data)
        if (browser != null) {
            browser.loadHTML(html)
        } else {
            fallbackLabel?.text = data.resource?.name ?: "No resource selected"
        }
    }

    private fun showLogin() {
        if (browser == null || loginQuery == null) return

        val html = InfracostResourceHtml.renderLogin(loginQuery.inject("''"))
        browser.loadHTML(html)
    }

    private fun performLogin() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val servers = LspServerManager.getInstance(project)
                    .getServersForProvider(InfracostLspServerSupportProvider::class.java)
                val server = servers.firstOrNull() ?: return@executeOnPooledThread
                val lsp = server.lsp4jServer as? InfracostLanguageServer ?: return@executeOnPooledThread

                val response = lsp.login().get(10, TimeUnit.SECONDS)
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
