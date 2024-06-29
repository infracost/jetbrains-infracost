package io.infracost.plugins.infracost.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.infracost.plugins.infracost.actions.tasks.InfracostAuthRunTask
import io.infracost.plugins.infracost.ui.InfracostWindow
import io.infracost.plugins.infracost.ui.TOOL_WINDOW_ID
import javax.swing.SwingUtilities

/** RunInfracostAction executes infracost then calls update results */
class RunAuthAction : AnAction() {
  private var project: Project? = null

  override fun actionPerformed(e: AnActionEvent) {
    this.project = e.project

    if (this.project == null) {
      return
    }

    runAuth(this.project!!)
  }

  companion object {
    fun runAuth(project: Project) {
      val runner =
          InfracostAuthRunTask(project) { isAuthenticated ->
            run {
              // redraw the explorer with the updated content
              val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
              val content = toolWindow!!.contentManager.getContent(0)
              (content!!.component as InfracostWindow).apply { updatePanel(isAuthenticated) }
            }
          }
      if (SwingUtilities.isEventDispatchThread()) {
        ProgressManager.getInstance().run(runner)
      } else {
        ApplicationManager.getApplication().invokeLater(runner)
      }
    }
  }
}
