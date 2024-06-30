package io.infracost.plugins.infracost.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.infracost.plugins.infracost.ui.InfracostWindow
import io.infracost.plugins.infracost.ui.TOOL_WINDOW_ID

/** ClearResultsAction removes the tree and findings */
class ClearResultsAction : AnAction() {
  private var project: Project? = null

  override fun actionPerformed(e: AnActionEvent) {
    this.project = e.project

    if (project == null) {
      return
    }
    val toolWindow = ToolWindowManager.getInstance(project!!).getToolWindow(TOOL_WINDOW_ID)
    val content = toolWindow!!.contentManager.getContent(0)
    val infracostWindow = content!!.component as InfracostWindow
    infracostWindow.clearModel()

    infracostWindow.redraw()
  }
}
