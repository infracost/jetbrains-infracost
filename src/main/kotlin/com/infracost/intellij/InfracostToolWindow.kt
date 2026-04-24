package com.infracost.intellij

import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest

data class LoginResult(
    val verificationUri: String = "",
    val verificationUriComplete: String = "",
    val userCode: String = "",
)

// Tree node types
sealed class InfracostTreeNode {
  data class Folder(val path: String) : InfracostTreeNode()

  data class File(val name: String, val uri: String) : InfracostTreeNode()

  data class Resource(
      val name: String,
      val uri: String,
      val line: Int,
      val policyIssues: Int,
      val tagIssues: Int,
  ) : InfracostTreeNode()
}

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
    toolWindow.setTitleActions(listOf(InfracostScanAction()))

    project.putUserData(PANEL_KEY, panel)

    project.messageBus
        .connect(toolWindow.disposable)
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

  private val cardLayout = CardLayout()
  private val cardPanel = JPanel(cardLayout)

  // Tree view components
  private val tree = Tree()
  private val filterField = SearchTextField(false)
  private val guardrailsPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
  private val footerPanel = JPanel(BorderLayout())
  private val treePanel = JPanel(BorderLayout())

  // Browser view components
  private val browserPanel = JPanel(BorderLayout())

  private val fallbackLabel: JLabel? =
      if (browser == null) {
        JLabel("JCEF is not available", SwingConstants.CENTER)
      } else null

  @Suppress("DEPRECATION")
  private val messageQuery: JBCefJSQuery? =
      browser?.let { b ->
        JBCefJSQuery.create(b).also { query ->
          query.addHandler { command ->
            ApplicationManager.getApplication().invokeLater {
              if (!project.isDisposed) handleCommand(command)
            }
            null
          }
        }
      }

  val sendCommand: (String) -> String = { cmd ->
    val escaped = cmd.replace("\\", "\\\\").replace("'", "\\'")
    messageQuery?.inject("'$escaped'") ?: ""
  }

  init {
    // Set up browser panel
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
      browserPanel.add(browser.component, BorderLayout.CENTER)
    } else {
      browserPanel.add(fallbackLabel!!, BorderLayout.CENTER)
    }

    // Set up tree
    tree.isRootVisible = false
    tree.showsRootHandles = true
    tree.model = DefaultTreeModel(DefaultMutableTreeNode())
    tree.cellRenderer = InfracostTreeCellRenderer()

    tree.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            val path = tree.getPathForLocation(e.x, e.y) ?: return
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
            if (node.userObject is InfracostTreeNode.Resource) {
              activateSelectedResource()
            }
          }
        }
    )
    tree.addKeyListener(
        object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER) activateSelectedResource()
          }
        }
    )

    // Filter
    filterField.addDocumentListener(
        object : DocumentListener {
          override fun insertUpdate(e: DocumentEvent) = applyFilter()
          override fun removeUpdate(e: DocumentEvent) = applyFilter()
          override fun changedUpdate(e: DocumentEvent) = applyFilter()
        }
    )

    // Assemble tree panel
    treePanel.add(filterField, BorderLayout.NORTH)
    treePanel.add(JScrollPane(tree), BorderLayout.CENTER)

    // Card layout
    cardPanel.add(treePanel, CARD_TREE)
    cardPanel.add(browserPanel, CARD_BROWSER)

    val topBar = JPanel(BorderLayout())
    topBar.add(guardrailsPanel, BorderLayout.CENTER)

    add(topBar, BorderLayout.NORTH)
    add(cardPanel, BorderLayout.CENTER)
    add(footerPanel, BorderLayout.SOUTH)

    // Start on browser (login/empty state)
    showBrowserView()
  }

  @Volatile private var lastParams: ResourceDetailsParams? = null
  @Volatile private var lastData: ResourceDetailsResult? = null
  @Volatile private var lastHtml: String? = null
  @Volatile private var showingTroubleshooting = false
  @Volatile private var activeGuardrails: List<GuardrailStatus> = emptyList()
  @Volatile private var selectedOrg: OrgEntry? = null
  @Volatile private var orgList: List<OrgEntry> = emptyList()
  @Volatile private var orgPromptShown = false
  @Volatile private var workspaceData: WorkspaceSummaryResult? = null
  @Volatile private var unfilteredRoot: DefaultMutableTreeNode? = null

  private fun showTreeView() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    cardLayout.show(cardPanel, CARD_TREE)
  }

  private fun showBrowserView() {
    if (ApplicationManager.getApplication().isDispatchThread) {
      cardLayout.show(cardPanel, CARD_BROWSER)
    } else {
      ApplicationManager.getApplication().invokeLater {
        if (!project.isDisposed) cardLayout.show(cardPanel, CARD_BROWSER)
      }
    }
  }

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
      showDefaultView()
      return
    }

    val html = InfracostResourceHtml.renderResult(data, sendCommand)
    setHtml(html)
    showBrowserView()
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
        val gson = GSON
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
    showDefaultView()
  }

  /** Shows tree view if workspace data available, otherwise browser empty view. */
  private fun showDefaultView() {
    val ws = workspaceData
    if (ws != null && !ws.files.isNullOrEmpty() && lastParams == null) {
      rebuildTree(ws)
      updateGuardrailsBanner()
      updateFooter()
      showTreeView()
    } else {
      val loggedIn = lastData?.needsLogin != true
      setHtmlOnEdt(InfracostResourceHtml.renderEmpty(sendCommand, loggedIn))
      showBrowserView()
    }
  }

  private fun showLogin() {
    if (browser == null) return
    setHtml(InfracostResourceHtml.renderLogin(sendCommand))
    showBrowserView()
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
        val gson = GSON
        val result: ResourceDetailsResult =
            gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)
                ?: return@executeOnPooledThread

        if (result.needsLogin == true) {
          ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) showLogin() }
        } else {
          ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) showDefaultView()
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

  // ── Tree building ──────────────────────────────────────────────────

  private class FolderNode(
      val name: String,
      val children: MutableMap<String, FolderNode> = sortedMapOf(),
      val fileEntries: MutableList<WorkspaceSummaryFile> = mutableListOf(),
  )

  private fun buildTreeModel(data: WorkspaceSummaryResult): DefaultMutableTreeNode {
    val files = data.files ?: return DefaultMutableTreeNode()

    val root = FolderNode("")
    for (file in files) {
      val path = file.name.ifBlank { file.uri.removePrefix("file://") }
      val parts = path.split("/").filter { it.isNotEmpty() }
      val dirs = parts.dropLast(1)
      var node = root
      for (dir in dirs) {
        node = node.children.getOrPut(dir) { FolderNode(dir) }
      }
      node.fileEntries.add(file)
    }

    val treeRoot = DefaultMutableTreeNode()
    addFolderChildren(treeRoot, root)
    return treeRoot
  }

  private fun addFolderChildren(parent: DefaultMutableTreeNode, folder: FolderNode) {
    for (child in folder.children.values) {
      // Collapse single-child chains
      var current = child
      var label = current.name
      while (current.fileEntries.isEmpty() && current.children.size == 1) {
        val next = current.children.values.first()
        label = "$label/${next.name}"
        current = next
      }

      val folderTreeNode = DefaultMutableTreeNode(InfracostTreeNode.Folder(label))
      addFolderChildren(folderTreeNode, current)
      parent.add(folderTreeNode)
    }

    for (file in folder.fileEntries) {
      addFileNode(parent, file)
    }
  }

  private fun addFileNode(parent: DefaultMutableTreeNode, file: WorkspaceSummaryFile) {
    val path = file.name.ifBlank { file.uri.removePrefix("file://") }
    val fileName = path.split("/").lastOrNull { it.isNotEmpty() } ?: path
    val fileNode = DefaultMutableTreeNode(InfracostTreeNode.File(fileName, file.uri))
    val resources = file.resources ?: emptyList()
    val sorted =
        resources.sortedWith(
            compareByDescending<WorkspaceSummaryResource> { it.policyIssues + it.tagIssues }
                .thenBy { it.name }
        )
    for (r in sorted) {
      fileNode.add(
          DefaultMutableTreeNode(
              InfracostTreeNode.Resource(r.name, file.uri, r.line, r.policyIssues, r.tagIssues)
          )
      )
    }
    parent.add(fileNode)
  }

  private fun rebuildTree(data: WorkspaceSummaryResult) {
    val root = buildTreeModel(data)
    unfilteredRoot = root
    tree.model = DefaultTreeModel(root)
    expandAllNodes()
  }

  private fun expandAllNodes() {
    var i = 0
    while (i < tree.rowCount) {
      tree.expandRow(i)
      i++
    }
  }

  // ── Filter ─────────────────────────────────────────────────────────

  private fun applyFilter() {
    val query = filterField.text.orEmpty().lowercase().trim()
    val source = unfilteredRoot ?: return

    if (query.isEmpty()) {
      tree.model = DefaultTreeModel(source)
      expandAllNodes()
      return
    }

    val filtered = filterNode(source, query)
    tree.model = DefaultTreeModel(filtered ?: DefaultMutableTreeNode())
    expandAllNodes()
  }

  private fun filterNode(node: DefaultMutableTreeNode, query: String): DefaultMutableTreeNode? {
    val obj = node.userObject
    when (obj) {
      is InfracostTreeNode.Resource -> {
        return if (obj.name.lowercase().contains(query)) {
          DefaultMutableTreeNode(obj)
        } else null
      }
      is InfracostTreeNode.File -> {
        val nameMatch = obj.name.lowercase().contains(query)
        val copy = DefaultMutableTreeNode(obj)
        for (i in 0 until node.childCount) {
          val child = node.getChildAt(i) as DefaultMutableTreeNode
          if (nameMatch) {
            copy.add(DefaultMutableTreeNode(child.userObject))
          } else {
            val filtered = filterNode(child, query)
            if (filtered != null) copy.add(filtered)
          }
        }
        return if (copy.childCount > 0) copy else null
      }
      is InfracostTreeNode.Folder -> {
        val copy = DefaultMutableTreeNode(obj)
        for (i in 0 until node.childCount) {
          val child = node.getChildAt(i) as DefaultMutableTreeNode
          val filtered = filterNode(child, query)
          if (filtered != null) copy.add(filtered)
        }
        return if (copy.childCount > 0) copy else null
      }
      else -> {
        // Root node
        val copy = DefaultMutableTreeNode()
        for (i in 0 until node.childCount) {
          val child = node.getChildAt(i) as DefaultMutableTreeNode
          val filtered = filterNode(child, query)
          if (filtered != null) copy.add(filtered)
        }
        return if (copy.childCount > 0) copy else null
      }
    }
  }

  // ── Guardrails banner ──────────────────────────────────────────────

  private fun updateGuardrailsBanner() {
    guardrailsPanel.removeAll()
    for (g in activeGuardrails) {
      val bg = if (g.blockPr) JBColor(Color(248, 215, 218), Color(90, 29, 29))
               else JBColor(Color(255, 243, 205), Color(70, 55, 15))
      val fg = if (g.blockPr) JBColor(Color(190, 17, 0), Color(244, 135, 113))
               else JBColor(Color(133, 100, 4), Color(204, 167, 0))
      val banner = JPanel(BorderLayout()).apply {
        background = bg
        border = JBUI.Borders.empty(6, 8)
      }
      val text = buildString {
        val icon = if (g.blockPr) "\u26D4" else "\u26A0\uFE0F"
        append("$icon ${escHtml(g.name)}")
        if (g.message.isNotBlank()) append(" — ${escHtml(g.message)}")
        if (!g.totalMonthlyCost.isNullOrBlank() && !g.threshold.isNullOrBlank()) {
          append("  Total: ${escHtml(g.totalMonthlyCost)}/mo  Limit: ${escHtml(g.threshold)}")
        }
      }
      val label = JLabel("<html>$text</html>").apply { foreground = fg }
      banner.add(label, BorderLayout.CENTER)
      guardrailsPanel.add(banner)
    }
    guardrailsPanel.revalidate()
    guardrailsPanel.repaint()
  }

  // ── Footer ─────────────────────────────────────────────────────────

  private fun updateFooter() {
    footerPanel.removeAll()
    footerPanel.border = JBUI.Borders.empty(4, 8)
    val org = selectedOrg
    if (org != null) {
      val orgLabel = JLabel(org.name).apply {
        foreground = JBColor.GRAY
        font = font.deriveFont(font.size2D - 1f)
      }
      footerPanel.add(orgLabel, BorderLayout.WEST)
      if (orgList.size > 1) {
        val link = JLabel(" · Switch organization").apply {
          foreground = com.intellij.util.ui.JBUI.CurrentTheme.Link.Foreground.ENABLED
          font = font.deriveFont(font.size2D - 1f)
          cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
          addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
              showOrgSelector()
            }
          })
        }
        footerPanel.add(link, BorderLayout.CENTER)
      }
    }
    footerPanel.revalidate()
    footerPanel.repaint()
  }

  // ── Tree actions ───────────────────────────────────────────────────

  private fun activateSelectedResource() {
    val selected = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
    val resource = selected.userObject as? InfracostTreeNode.Resource ?: return
    navigateToResource(resource.uri, resource.line)
  }

  // ── Command handling ───────────────────────────────────────────────

  private fun handleCommand(command: String) {
    when {
      command == "login" -> performLogin()
      command == "cancelLogin" -> showLogin()
      command == "logout" -> performLogout()
      command == "switchOrg" -> showOrgSelector()
      command == "troubleshoot" -> {
        showingTroubleshooting = true
        showTroubleshooting()
      }
      command == "back" -> {
        showingTroubleshooting = false
        lastParams = null
        lastData = ResourceDetailsResult(resource = null, scanning = false, needsLogin = false)
        showDefaultView()
      }
      command == "viewLogs" -> {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Language Servers")
        toolWindow?.show()
      }
      command == "restartLsp" -> restartLsp()
      command == "generateBundle" -> generateSupportBundle()
      command == "openSettings" -> {
        com.intellij.openapi.options.ShowSettingsUtil.getInstance()
            .showSettingsDialog(project, "com.infracost.intellij.InfracostSettingsConfigurable")
      }
      command.startsWith("revealResource:") -> {
        val line = command.removePrefix("revealResource:").toIntOrNull() ?: return
        revealResource(line)
      }
      command.startsWith("navigate:") -> {
        val payload = command.removePrefix("navigate:")
        val lastColon = payload.lastIndexOf(':')
        if (lastColon > 0) {
          val uri = payload.substring(0, lastColon)
          val line = payload.substring(lastColon + 1).toIntOrNull() ?: 0
          navigateToResource(uri, line)
        }
      }
    }
  }

  fun onLoginComplete() {
    checkAuthStatus()
  }

  fun onLogoutComplete() {
    lastParams = null
    lastData = null
    workspaceData = null
    showLogin()
  }

  fun triggerScan() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        // Send a didSave for the workspace root to trigger a scan
        val basePath = project.basePath ?: return@executeOnPooledThread
        val uri = java.nio.file.Path.of(basePath).toUri().toString()
        val saveParams = org.eclipse.lsp4j.DidSaveTextDocumentParams(
            org.eclipse.lsp4j.TextDocumentIdentifier(uri),
        )
        server.sendNotification { it.textDocumentService.didSave(saveParams) }
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            InfracostStatusBarWidget.getInstance(project)?.show("Infracost: Scanning...")
          }
        }
      } catch (e: Exception) {
        LOG.warn("Failed to trigger scan", e)
      }
    }
  }

  fun fetchWorkspaceSummary() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response =
            server.sendRequestSync { (it as InfracostLanguageServer).workspaceSummary() }
        val gson = GSON
        val result: WorkspaceSummaryResult? =
            gson.fromJson(gson.toJson(response), WorkspaceSummaryResult::class.java)
        workspaceData = result
        if (result != null && !result.files.isNullOrEmpty()) {
          ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && lastParams == null && !showingTroubleshooting) {
              rebuildTree(result)
              updateGuardrailsBanner()
              updateFooter()
              showTreeView()
            }
          }
        }
      } catch (e: Exception) {
        LOG.debug("Failed to fetch workspace summary", e)
      }
    }
  }

  fun fetchGuardrails() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response = server.sendRequestSync { (it as InfracostLanguageServer).status() }
        val gson = GSON
        val status: StatusInfo? =
            gson.fromJson(gson.toJson(response), StatusInfo::class.java)
        activeGuardrails = status?.triggeredGuardrails ?: emptyList()
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) updateGuardrailsBanner()
        }
      } catch (e: Exception) {
        LOG.debug("Failed to fetch guardrails", e)
      }
    }
  }

  fun fetchOrgs() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response = server.sendRequestSync { (it as InfracostLanguageServer).orgs() }
        val gson = GSON
        val result: OrgInfo? = gson.fromJson(gson.toJson(response), OrgInfo::class.java)
        val orgs = result?.organizations ?: return@executeOnPooledThread
        orgList = orgs
        selectedOrg = orgs.find { it.id == result.selectedOrgId }
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) updateFooter()
        }
        if (orgs.size > 1 && !result.hasExplicitSelection && !orgPromptShown) {
          orgPromptShown = true
          ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) showOrgSelector(orgs)
          }
        }
      } catch (e: Exception) {
        LOG.debug("Failed to fetch orgs", e)
      }
    }
  }

  fun showOrgSelector(orgs: List<OrgEntry> = orgList) {
    if (orgs.isEmpty()) return
    val step =
        object : BaseListPopupStep<OrgEntry>("Select Organization", orgs) {
          override fun getTextFor(value: OrgEntry): String = "${value.name} (${value.slug})"

          override fun onChosen(selectedValue: OrgEntry, finalChoice: Boolean): PopupStep<*>? {
            selectOrg(selectedValue)
            return FINAL_CHOICE
          }
        }
    JBPopupFactory.getInstance().createListPopup(step).showInFocusCenter()
  }

  private fun selectOrg(org: OrgEntry) {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        server.sendRequestSync {
          (it as InfracostLanguageServer).selectOrg(SelectOrgParams(org.id))
        }
        selectedOrg = org
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            @Suppress("UnstableApiUsage")
            LspServerManager.getInstance(project)
                .stopAndRestartIfNeeded(InfracostLspServerSupportProvider::class.java)
          }
        }
      } catch (e: Exception) {
        LOG.warn("Failed to select org", e)
      }
    }
  }

  fun performLogout() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        server.sendRequestSync { (it as InfracostLanguageServer).logout() }
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            lastParams = null
            lastData = null
            workspaceData = null
            showLogin()
          }
        }
      } catch (e: Exception) {
        LOG.warn("Infracost logout failed", e)
      }
    }
  }

  private fun performLogin() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response = server.sendRequestSync { (it as InfracostLanguageServer).login() }
        val gson = GSON
        val result: LoginResult? = gson.fromJson(gson.toJson(response), LoginResult::class.java)
        if (result == null) {
          LOG.warn("Infracost login returned null result")
          return@executeOnPooledThread
        }

        ApplicationManager.getApplication().invokeLater {
          if (project.isDisposed) return@invokeLater
          setHtml(InfracostResourceHtml.renderLoginWaiting(result.userCode, sendCommand))
          showBrowserView()
          BrowserUtil.browse(result.verificationUriComplete)
        }
      } catch (e: Exception) {
        LOG.warn("Infracost login failed", e)
        ApplicationManager.getApplication().invokeLater {
          if (project.isDisposed) return@invokeLater
          setHtml(InfracostResourceHtml.renderLoginFailed(sendCommand))
          showBrowserView()
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
            showBrowserView()
          }
          return@executeOnPooledThread
        }
        val response = server.sendRequestSync { (it as InfracostLanguageServer).status() }
        val gson = GSON
        val status = gson.fromJson(gson.toJson(response), StatusInfo::class.java) ?: StatusInfo()
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) {
            setHtml(InfracostResourceHtml.renderTroubleshooting(sendCommand, status))
            showBrowserView()
          }
        }
      } catch (e: Exception) {
        LOG.debug("Failed to fetch status", e)
        ApplicationManager.getApplication().invokeLater {
          if (project.isDisposed) return@invokeLater
          setHtml(
              InfracostResourceHtml.renderTroubleshooting(
                  sendCommand,
                  StatusInfo(version = "error fetching status"),
              )
          )
          showBrowserView()
        }
      }
    }
  }

  private fun navigateToResource(uri: String, line: Int) {
    val vf = com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(uri)
    if (vf != null) {
      ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        FileEditorManager.getInstance(project).openFile(vf, true)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor != null && editor.document.lineCount > 0) {
          val offset =
              editor.document.getLineStartOffset(line.coerceIn(0, editor.document.lineCount - 1))
          editor.caretModel.moveToOffset(offset)
          editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
        }
      }
    }
    val params = ResourceDetailsParams(uri, line)
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response = server.sendRequestSync {
          (it as InfracostLanguageServer).resourceDetails(params)
        }
        val gson = GSON
        val result: ResourceDetailsResult =
            gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)
                ?: return@executeOnPooledThread
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) update(params, result)
        }
      } catch (e: Exception) {
        LOG.debug("Failed to navigate to resource", e)
      }
    }
  }

  private fun revealResource(line: Int) {
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val uri = editor.virtualFile?.url ?: return
    if (editor.document.lineCount == 0) return
    val params = ResourceDetailsParams(uri, line)

    val offset = editor.document.getLineStartOffset(line.coerceIn(0, editor.document.lineCount - 1))
    editor.caretModel.moveToOffset(offset)
    editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer() ?: return@executeOnPooledThread
        val response = server.sendRequestSync {
          (it as InfracostLanguageServer).resourceDetails(params)
        }
        val gson = GSON
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

  private fun restartLsp() {
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      try {
        @Suppress("UnstableApiUsage")
        LspServerManager.getInstance(project)
            .stopAndRestartIfNeeded(InfracostLspServerSupportProvider::class.java)
        notifyInfo(project, "Infracost language server restarted")
      } catch (e: Exception) {
        LOG.warn("Failed to restart LSP", e)
        notifyError(project, "Failed to restart Infracost language server")
      }
    }
  }

  private fun generateSupportBundle() {
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        val server = getLspServer()
        val gson = GSON
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

  private fun setHtmlOnEdt(html: String) {
    if (ApplicationManager.getApplication().isDispatchThread) {
      setHtml(html)
    } else {
      ApplicationManager.getApplication().invokeLater { if (!project.isDisposed) setHtml(html) }
    }
  }

  private fun getLspServer() =
      LspServerManager.getInstance(project)
          .getServersForProvider(InfracostLspServerSupportProvider::class.java)
          .firstOrNull()

  companion object {
    private val LOG = Logger.getInstance(InfracostToolWindowPanel::class.java)
    private const val CARD_TREE = "tree"
    private const val CARD_BROWSER = "browser"
    private val GSON = Gson()

    private fun escHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
  }
}

// ── Cell renderer ──────────────────────────────────────────────────

private class InfracostTreeCellRenderer : javax.swing.tree.TreeCellRenderer {
  private val inner = object : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: javax.swing.JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
      val node = (value as? DefaultMutableTreeNode)?.userObject ?: return
      when (node) {
        is InfracostTreeNode.Folder -> {
          icon = com.intellij.icons.AllIcons.Nodes.Folder
          append(node.path)
        }
        is InfracostTreeNode.File -> {
          icon = com.intellij.icons.AllIcons.FileTypes.Text
          append(node.name)
        }
        is InfracostTreeNode.Resource -> {
          append(node.name)
        }
    }
  }
  }

  private val issueLabel = JLabel().apply {
    foreground = JBColor(Color(180, 130, 0), Color(204, 167, 0))
  }
  private val wrapper = JPanel(BorderLayout()).apply { isOpaque = false }

  override fun getTreeCellRendererComponent(
      tree: javax.swing.JTree,
      value: Any?,
      selected: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean,
  ): java.awt.Component {
    val comp = inner.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    val node = (value as? DefaultMutableTreeNode)?.userObject
    if (node is InfracostTreeNode.Resource) {
      val issues = node.policyIssues + node.tagIssues
      if (issues > 0) {
        issueLabel.text = "$issues issue${if (issues != 1) "s" else ""}  "
        wrapper.removeAll()
        wrapper.add(comp, BorderLayout.CENTER)
        wrapper.add(issueLabel, BorderLayout.EAST)
        return wrapper
      }
    }
    return comp
  }
}
