package com.infracost.intellij

import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.Alarm
import java.awt.BorderLayout
import java.io.File
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest

data class LoginResult(
    val verificationUri: String = "",
    val verificationUriComplete: String = "",
    val userCode: String = "",
)

class InfracostToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel =
        try {
          val browser = JBCefBrowser()
          val p = InfracostToolWindowPanel(project, browser)
          browser.loadHTML(InfracostResourceHtml.renderEmpty(p.sendCommand))
          p
        } catch (_: Exception) {
          InfracostToolWindowPanel(project, null)
        }

    val content = toolWindow.contentManager.factory.createContent(panel, "", false)
    toolWindow.contentManager.addContent(content)

    project.putUserData(PANEL_KEY, panel)

    project.messageBus
        .connect()
        .subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
              override fun toolWindowShown(tw: ToolWindow) {
                if (tw.id == ID) {
                  panel.checkAuthStatus()
                }
              }
            },
        )

    panel.checkAuthStatus()
  }

  companion object {
    const val ID = "Infracost"
    val PANEL_KEY =
        com.intellij.openapi.util.Key.create<InfracostToolWindowPanel>("infracost.panel")

    fun show(project: Project, params: ResourceDetailsParams, data: ResourceDetailsResult) {
      ApplicationManager.getApplication().invokeLater {
        val toolWindow =
            ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return@invokeLater
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

  private val fallbackLabel: JLabel? =
      if (browser == null) {
        JLabel("JCEF is not available", SwingConstants.CENTER)
      } else null

  @Suppress("DEPRECATION")
  private val messageQuery: JBCefJSQuery? = browser?.let { b ->
    JBCefJSQuery.create(b).also { query ->
      query.addHandler { command ->
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) handleCommand(command)
        }
        null
      }
    }
  }

  /** Generates JS code that sends a command string back to the Kotlin handler. */
  val sendCommand: (String) -> String = { cmd ->
    val escaped = cmd.replace("\\", "\\\\").replace("'", "\\'")
    messageQuery?.inject("'$escaped'") ?: ""
  }

  init {
    if (browser != null) {
      browser.jbCefClient.addRequestHandler(
          object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                b: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                userGesture: Boolean,
                isRedirect: Boolean,
            ): Boolean {
              val url = request?.url ?: return false
              if (url.startsWith("http://") || url.startsWith("https://")) {
                BrowserUtil.browse(url)
                return true
              }
              return false
            }
          },
          browser.cefBrowser,
      )
      add(browser.component, BorderLayout.CENTER)
    } else {
      add(fallbackLabel!!, BorderLayout.CENTER)
    }
  }

  @Volatile private var lastParams: ResourceDetailsParams? = null
  @Volatile private var lastData: ResourceDetailsResult? = null
  @Volatile private var lastHtml: String? = null
  @Volatile private var showingTroubleshooting = false

  /** Must be called on the EDT. */
  fun update(params: ResourceDetailsParams, data: ResourceDetailsResult) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    showingTroubleshooting = false
    lastParams = params
    lastData = data

    if (data.needsLogin == true) {
      showLogin()
      return
    }

    if (data.resource == null && !data.scanning) {
      fetchAndRenderEmpty(loggedIn = true)
      return
    }

    val html = InfracostResourceHtml.renderResult(data, sendCommand)
    setHtml(html)
  }

  fun refreshCurrentResource() {
    if (showingTroubleshooting) return
    val params = lastParams
    if (params == null) {
      refreshEmpty()
      return
    }
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response = server.sendRequestSync {
          (it as InfracostLanguageServer).resourceDetails(params)
        }
        val gson = Gson()
        val result: ResourceDetailsResult =
            gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)
                ?: return@executeOnPooledThread
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) update(params, result)
        }
      } catch (e: Exception) {
        LOG.debug("Failed to refresh current resource", e)
      }
    }
  }

  fun refreshEmpty() {
    if (showingTroubleshooting) return
    val data = lastData
    if (data?.resource != null) return
    fetchAndRenderEmpty(loggedIn = data?.needsLogin != true)
  }

  private fun showLogin() {
    if (browser == null) return
    setHtml(InfracostResourceHtml.renderLogin(sendCommand))
  }

  fun checkAuthStatus(retriesLeft: Int = 3) {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer()
        if (server == null) {
          retryCheckAuthStatus(retriesLeft)
          return@executeOnPooledThread
        }
        val response = server.sendRequestSync {
          (it as InfracostLanguageServer).resourceDetails(ResourceDetailsParams("", 0))
        }
        val gson = Gson()
        val result: ResourceDetailsResult =
            gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)
                ?: return@executeOnPooledThread

        if (result.needsLogin == true) {
          ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) showLogin() }
        } else {
          ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) fetchAndRenderEmpty(loggedIn = true)
          }
        }
      } catch (_: Exception) {
        retryCheckAuthStatus(retriesLeft)
      }
    }
  }

  private val retryAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)

  private fun retryCheckAuthStatus(retriesLeft: Int) {
    if (retriesLeft <= 0 || project.isDisposed) return
    retryAlarm.addRequest({ checkAuthStatus(retriesLeft - 1) }, 5000)
  }

  private fun handleCommand(command: String) {
    when {
      command == "login" -> performLogin()
      command == "troubleshoot" -> {
        showingTroubleshooting = true
        showTroubleshooting()
      }
      command == "back" -> {
        showingTroubleshooting = false
        lastParams = null
        lastData = ResourceDetailsResult(resource = null, scanning = false, needsLogin = false)
        fetchAndRenderEmpty(loggedIn = true)
      }
      command == "viewLogs" -> {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Language Servers")
        toolWindow?.show()
      }
      command == "restartLsp" -> restartLsp()
      command == "generateBundle" -> generateSupportBundle()
      command.startsWith("revealResource:") -> {
        val line = command.removePrefix("revealResource:").toIntOrNull() ?: return
        revealResource(line)
      }
    }
  }

  private fun performLogin() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response = server.sendRequestSync { (it as InfracostLanguageServer).login() }
        val gson = Gson()
        val result: LoginResult? = gson.fromJson(gson.toJson(response), LoginResult::class.java)
        if (result == null) {
          LOG.warn("Infracost login returned null result")
          return@executeOnPooledThread
        }

        ApplicationManager.getApplication().invokeLater {
          if (project.isDisposed) return@invokeLater

          notifyInfo(project, "Verify the code in your browser matches: ${result.userCode}")
              .addAction(
                  object : NotificationAction("Open Browser") {
                    override fun actionPerformed(e: AnActionEvent, n: Notification) {
                      BrowserUtil.browse(result.verificationUriComplete)
                      n.expire()
                      fetchAndRenderEmpty(loggedIn = true)
                    }
                  }
              )
        }
      } catch (e: Exception) {
        LOG.warn("Infracost login failed", e)
        ApplicationManager.getApplication().invokeLater {
          setHtml(
              InfracostResourceHtml.renderPage(
                  """<div class="state"><p>Login failed. Please try again.</p></div>"""
              )
          )
        }
      }
    }
  }

  private fun showTroubleshooting() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer()
        if (server == null) {
          ApplicationManager.getApplication().invokeLater {
            setHtml(InfracostResourceHtml.renderTroubleshooting(sendCommand, StatusInfo()))
          }
          return@executeOnPooledThread
        }
        val response = server.sendRequestSync { (it as InfracostLanguageServer).status() }
        val gson = Gson()
        val status = gson.fromJson(gson.toJson(response), StatusInfo::class.java) ?: StatusInfo()
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            setHtml(InfracostResourceHtml.renderTroubleshooting(sendCommand, status))
          }
        }
      } catch (e: Exception) {
        LOG.debug("Failed to fetch status", e)
        ApplicationManager.getApplication().invokeLater {
          setHtml(
              InfracostResourceHtml.renderTroubleshooting(
                  sendCommand,
                  StatusInfo(version = "error fetching status"),
              )
          )
        }
      }
    }
  }

  private fun fetchAndRenderEmpty(loggedIn: Boolean) {
    val uri = activeEditorUri()
    if (uri == null) {
      setHtmlOnEdt(InfracostResourceHtml.renderEmpty(sendCommand, loggedIn))
      return
    }

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer()
        if (server == null) {
          ApplicationManager.getApplication().invokeLater {
            setHtml(InfracostResourceHtml.renderEmpty(sendCommand, loggedIn))
          }
          return@executeOnPooledThread
        }
        val response = server.sendRequestSync {
          (it as InfracostLanguageServer).fileSummary(FileSummaryParams(uri))
        }
        val gson = Gson()
        val result: FileSummaryResult? =
            gson.fromJson(gson.toJson(response), FileSummaryResult::class.java)
        val resources = result?.resources?.takeIf { it.isNotEmpty() }
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            setHtml(InfracostResourceHtml.renderEmpty(sendCommand, loggedIn, resources))
          }
        }
      } catch (e: Exception) {
        LOG.debug("Failed to fetch file summary", e)
        ApplicationManager.getApplication().invokeLater {
          setHtml(InfracostResourceHtml.renderEmpty(sendCommand, loggedIn))
        }
      }
    }
  }

  private fun restartLsp() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        @Suppress("UnstableApiUsage")
        LspServerManager.getInstance(project)
            .stopAndRestartIfNeeded(InfracostLspServerSupportProvider::class.java)
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            notifyInfo(project, "Infracost language server restarted")
          }
        }
      } catch (e: Exception) {
        LOG.warn("Failed to restart LSP", e)
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            notifyError(project, "Failed to restart Infracost language server")
          }
        }
      }
    }
  }

  private fun generateSupportBundle() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer()
        val gson = Gson()
        val bundle = mutableMapOf<String, Any?>()

        bundle["timestamp"] = System.currentTimeMillis()
        bundle["os"] = System.getProperty("os.name")
        bundle["arch"] = System.getProperty("os.arch")
        bundle["ideVersion"] =
            com.intellij.openapi.application.ApplicationInfo.getInstance().fullVersion

        if (server != null) {
          val response = server.sendRequestSync { (it as InfracostLanguageServer).status() }
          val status: StatusInfo? = gson.fromJson(gson.toJson(response), StatusInfo::class.java)
          bundle["lspStatus"] = status ?: StatusInfo()
        }

        val json = gson.toJson(bundle)
        val safeName = (project.name).replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file =
            File(System.getProperty("java.io.tmpdir"), "infracost-support-bundle-$safeName.json")
        file.writeText(json)

        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            notifyInfo(project, "Support bundle saved to ${file.absolutePath}")
          }
        }
      } catch (e: Exception) {
        LOG.warn("Failed to generate support bundle", e)
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            notifyError(project, "Failed to generate support bundle")
          }
        }
      }
    }
  }

  private fun revealResource(line: Int) {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val uri = editor.virtualFile?.url ?: return
    if (editor.document.lineCount == 0) return
    val params = ResourceDetailsParams(uri, line)

    // Move cursor to the resource line
    val offset = editor.document.getLineStartOffset(line.coerceIn(0, editor.document.lineCount - 1))
    editor.caretModel.moveToOffset(offset)
    editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response = server.sendRequestSync {
          (it as InfracostLanguageServer).resourceDetails(params)
        }
        val gson = Gson()
        val result: ResourceDetailsResult =
            gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)
                ?: return@executeOnPooledThread
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) update(params, result)
        }
      } catch (e: Exception) {
        LOG.debug("Failed to reveal resource", e)
      }
    }
  }

  private fun setHtml(html: String) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (html == lastHtml) return
    lastHtml = html
    if (browser != null) {
      browser.loadHTML(html)
    } else {
      fallbackLabel?.text = "No resource selected"
    }
  }

  /** Dispatch setHtml to EDT if not already on it. */
  private fun setHtmlOnEdt(html: String) {
    if (ApplicationManager.getApplication().isDispatchThread) {
      setHtml(html)
    } else {
      ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) setHtml(html) }
    }
  }

  private fun activeEditorUri(): String? {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
    return editor.virtualFile?.url
  }

  private fun getLspServer() =
      LspServerManager.getInstance(project)
          .getServersForProvider(InfracostLspServerSupportProvider::class.java)
          .firstOrNull()

  companion object {
    private val LOG = Logger.getInstance(InfracostToolWindowPanel::class.java)
  }
}
