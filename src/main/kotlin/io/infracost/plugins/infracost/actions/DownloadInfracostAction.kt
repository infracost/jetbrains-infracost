package io.infracost.plugins.infracost.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.actions.tasks.InfracostDownloadBinaryTask
import javax.swing.SwingUtilities

class DownloadInfracostAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        ProgressManager.getInstance().run(InfracostDownloadBinaryTask(e.project!!, false))
    }

    companion object {
        fun runDownload(project: Project, initial: Boolean = false) {
            val runner =
                InfracostDownloadBinaryTask(project, initial)
            if (SwingUtilities.isEventDispatchThread()) {
                ProgressManager.getInstance().run(runner)
            } else {
                ApplicationManager.getApplication().invokeLater(runner)
            }
        }
    }
}