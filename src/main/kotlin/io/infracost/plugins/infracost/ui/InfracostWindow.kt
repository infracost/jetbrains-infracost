package io.infracost.plugins.infracost.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import io.infracost.plugins.infracost.actions.DownloadInfracostAction
import io.infracost.plugins.infracost.actions.ResultProcessor
import io.infracost.plugins.infracost.actions.RunAuthAction
import io.infracost.plugins.infracost.model.Resource
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Paths
import javax.swing.*
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

const val TOOL_WINDOW_ID: String = "Infracost: Projects Overview"

class InfracostWindow(private val project: Project) : SimpleToolWindowPanel(false, true) {
    private var root: Tree? = null
    private var authenticated: Boolean = false

    init {
        DownloadInfracostAction.runDownload(project)
        configureToolbar()

    }

    fun updatePanel(isAuthenticated: Boolean) {
        if (isAuthenticated) {
            this.authenticated = true
            this.removeAll()
            configureToolbar()
        } else {
            this.authenticated = false
            showLoginButton()
        }
    }

    private fun showLoginButton() {
        val label = "First we need to"
        val hyperlink = JBLabel("connect to Infracost")
        hyperlink.foreground = JBColor.BLUE
        hyperlink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        hyperlink.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                RunAuthAction.runAuth(project)
            }
        })

        val panel = JPanel()
        panel.border = BorderFactory.createEmptyBorder(20, 10, 0, 10)
        panel.add(JLabel(label))
        panel.add(hyperlink)
        this.add(JBScrollPane(panel))
    }

    private fun configureToolbar() {
        val actionManager = ActionManager.getInstance()

        val actionGroup = DefaultActionGroup("ACTION_GROUP", false)

        actionGroup.add(
            actionManager.getAction("io.infracost.plugins.infracost.actions.RunInfracostAction")
        )
        actionGroup.add(
            actionManager.getAction("io.infracost.plugins.infracost.actions.ClearResultsAction")
        )
        actionGroup.add(
            actionManager.getAction(
                "io.infracost.plugins.infracost.actions.ShowInfracostSettingsAction"
            )
        )

        val actionToolbar = actionManager.createActionToolbar("ACTION_TOOLBAR", actionGroup, true)
        actionToolbar.setOrientation(SwingConstants.VERTICAL)
        this.toolbar = actionToolbar.component
    }

    override fun getContent(): JComponent? {
        return this.component
    }

    private fun JTree.setModelAndMaintainExpanded(newModel: TreeModel) {
        // Step 1: Store the expanded paths
        val expandedPaths = saveExpandedPaths(this)

        // Step 2: Set the new TreeModel
        this.model = newModel

        // Step 3: Restore the expanded paths
        restoreExpandedPaths(this, expandedPaths)
    }

    private fun saveExpandedPaths(tree: JTree): List<TreePath> {
        val expandedPaths = mutableListOf<TreePath>()
        val rowCount = tree.rowCount

        for (i in 0 until rowCount) {
            val path = tree.getPathForRow(i)
            if (tree.isExpanded(path)) {
                expandedPaths.add(path)
            }
        }

        return expandedPaths
    }

    private fun restoreExpandedPaths(tree: JTree, paths: List<TreePath>) {
        for (path in paths) {
            tree.expandPath(path)
        }
    }

    fun clearModel() {
        this.root?.model = null
    }

    fun refreshModel() {
        if (ResultProcessor.model == null) {
            return
        }

        if (this.root == null) {
            this.root = Tree(ResultProcessor.model)
            this.root!!.isRootVisible = false
            this.root!!.border = null
            this.root!!.autoscrolls = true

            this.root!!.cellRenderer = InfracostTreeRenderer()
            this.root!!.addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(me: MouseEvent) {
                        doMouseClicked()
                    }
                })
            this.removeAll()
            this.add(JBScrollPane(this.root))
        } else {
            this.root!!.setModelAndMaintainExpanded(ResultProcessor.model!!)
            this.root!!.updateUI()
        }
    }

    fun doMouseClicked() {
        val lastSelectedNode = root!!.lastSelectedPathComponent ?: return
        if (lastSelectedNode is Resource) {
            val location = lastSelectedNode.metadata ?: return
            val file =
                VirtualFileManager.getInstance()
                    .refreshAndFindFileByNioPath(
                        Paths.get(
                            this.project.basePath.toString(),
                            location.filename.toString()
                        )
                    )
            if (file == null || !file.exists()) {
                return
            }
            val ofd = OpenFileDescriptor(project, file, location.startLine - 1, 0)
            val editor = FileEditorManager.getInstance(project).openTextEditor(ofd, true) ?: return
            editor.caretModel.moveToOffset(editor.document.getLineEndOffset(location.startLine - 1))
        }
    }

    fun redraw() {
        configureToolbar()
        this.validate()
        this.repaint()
    }
}
