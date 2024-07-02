package io.infracost.plugins.infracost.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.infracost.plugins.infracost.actions.tasks.InfracostCheckAuthRunTask
import io.infracost.plugins.infracost.ui.InfracostWindow
import io.infracost.plugins.infracost.ui.TOOL_WINDOW_ID
import javax.swing.SwingUtilities

/** RunInfracostAction executes infracost then calls update results */
class CheckAuthAction : AnAction() {
    private var project: Project? = null

    override fun actionPerformed(e: AnActionEvent) {
        this.project = e.project

        if (this.project == null) {
            return
        }

        checkAuth(this.project!!)
    }

    companion object {
        var isAuthenticated: Boolean = false

        fun checkAuth(project: Project) {
            val runner =
                InfracostCheckAuthRunTask(project) { apiKey: String ->
                    // redraw the explorer with the updated content
                    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
                    val content = toolWindow!!.contentManager.getContent(0)
                    (content!!.component as InfracostWindow).apply {
                        isAuthenticated = apiKey.isNotEmpty() && apiKey.startsWith("ico")
                        updatePanel(isAuthenticated)
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
