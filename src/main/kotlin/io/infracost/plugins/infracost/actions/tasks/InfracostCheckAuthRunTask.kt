package io.infracost.plugins.infracost.actions.tasks

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.binary.InfracostBinary
import io.infracost.plugins.infracost.ui.notify.InfracostNotificationGroup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.function.Consumer
import javax.swing.SwingUtilities

internal class InfracostCheckAuthRunTask(
    private val project: Project,
    private val callback: Consumer<String>
) : Backgroundable(project, "Checking Infracost Auth Status", false), Runnable {
    override fun run(indicator: ProgressIndicator) {
        this.run()
    }

    override fun run() {
        val commandParts: MutableList<String?> = ArrayList()
        commandParts.add(InfracostBinary.binaryFile)
        commandParts.add("configure")
        commandParts.add("get")
        commandParts.add("api_key")

        try {
            val command = ProcessBuilder(commandParts)
            command.environment().set("INFRACOST_CLI_PLATFORM", "jetbrains")
            command.environment().set("INFRACOST_SKIP_UPDATE_CHECK", "true")
            command.environment().set("INFRACOST_GRAPH_EVALUATOR", "true")
            command.environment().set("INFRACOST_NO_COLOR", "true")

            val process = command.start()

            val inputReader = BufferedReader(InputStreamReader(process.inputStream))
            val inputThread = Thread {
                try {
                    inputReader.forEachLine { line ->
                        SwingUtilities.invokeLater { callback.accept(line) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    inputReader.close()
                }
            }
            inputThread.start()

            // Wait for the process to complete
            process.waitFor()
        } catch (e: ExecutionException) {
            InfracostNotificationGroup.notifyError(project, e.localizedMessage)
        }
    }
}
