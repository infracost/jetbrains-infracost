package io.infracost.plugins.infracost.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.actions.tasks.InfracostBackgroundRunTask
import io.infracost.plugins.infracost.ui.notify.InfracostNotificationGroup
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/** RunInfracostAction executes infracost then calls update results */
class RunInfracostAction : AnAction() {
    private var project: Project? = null

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = CheckAuthAction.isAuthenticated
    }

    override fun actionPerformed(e: AnActionEvent) {
        this.project = e.project

        if (this.project == null) {
            return
        }

        runInfracost(this.project!!)
    }

    companion object {
        private val running = AtomicBoolean(false)
        private val next = AtomicReference<InfracostBackgroundRunTask>()

        fun runInfracost(project: Project) {
            var resultFile: File? = null
            try {
                resultFile = File.createTempFile("infracost", ".json")
            } catch (ex: IOException) {
                InfracostNotificationGroup.notifyError(project, ex.localizedMessage)
            }

            // mark the file for deletion on exit
            resultFile?.deleteOnExit()

            val runner =
                InfracostBackgroundRunTask(project, resultFile!!) { proj: Project, f: File? ->
                    ResultProcessor.updateResults(proj, f)
                    running.set(false)
                    val queued = next.getAndSet(null)
                    if (queued != null) {
                        running.set(true)
                        ProgressManager.getInstance().run(queued)
                    }
                }

            if (running.get()) {
                // If a run is already in progress, queue the next run if not already queued
                next.compareAndSet(null, runner)
                return
            }

            if (SwingUtilities.isEventDispatchThread()) {
                running.set(true)
                ProgressManager.getInstance().run(runner)
            } else {
                ApplicationManager.getApplication().invokeLater(runner)
            }
        }
    }
}
