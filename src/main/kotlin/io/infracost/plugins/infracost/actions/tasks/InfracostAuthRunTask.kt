package io.infracost.plugins.infracost.actions.tasks

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.actions.CheckAuthAction
import io.infracost.plugins.infracost.ui.notify.InfracostNotificationGroup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.function.Consumer
import javax.swing.SwingUtilities

@Suppress("DialogTitleCapitalization")
internal class InfracostAuthRunTask(
    private val project: Project,
    private val callback: Consumer<Boolean>
) : InfracostTask(project, "Authenticate Infracost", false), Runnable {
  override fun run(indicator: ProgressIndicator) {
    this.run()
  }

  override fun run() {
    if (!ensureBinaryAvailable()) {
      InfracostNotificationGroup.notifyError(project, "Infracost binary not found")
      return
    }

    val commandParams: MutableList<String?> = ArrayList()
    commandParams.add(binaryFile)
    commandParams.add("auth")
    commandParams.add("login")

    val commandLine =
        GeneralCommandLine(commandParams)
            .withEnvironment(
                mapOf(
                    "INFRACOST_SKIP_UPDATE_CHECK" to "true",
                    "INFRACOST_GRAPH_EVALUATOR" to "true",
                    "INFRACOST_NO_COLOR" to "true",
                    "INFRACOST_CLI_PLATFORM" to "vscode",
                ))
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        try {
          val process = Runtime.getRuntime().exec(commandLine.commandLineString)
          val inputReader = BufferedReader(InputStreamReader(process.inputStream))
          val inputThread = Thread {
            try {
              inputReader.forEachLine { line ->
                if (line.contains("Your account has been authenticated")) {
                  CheckAuthAction.isAuthenticated = true
                  SwingUtilities.invokeLater { this.callback.accept(true) }
                }
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
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }
}
