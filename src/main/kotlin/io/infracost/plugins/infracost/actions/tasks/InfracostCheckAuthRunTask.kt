package io.infracost.plugins.infracost.actions.tasks

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.ui.notify.InfracostNotificationGroup
import java.util.function.Consumer
import javax.swing.SwingUtilities

internal class InfracostCheckAuthRunTask(
    private val project: Project,
    private val callback: Consumer<String>
) : InfracostTask(project, "Checking Infracost Auth Status", false), Runnable {
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
    commandParams.add("configure")
    commandParams.add("get")
    commandParams.add("api_key")

    val commandLine =
        GeneralCommandLine(commandParams)
            .withEnvironment(
                mapOf(
                    "INFRACOST_SKIP_UPDATE_CHECK" to "true",
                    "INFRACOST_GRAPH_EVALUATOR" to "true",
                    "INFRACOST_NO_COLOR" to "true"))

    try {
      Runtime.getRuntime().exec(commandLine.commandLineString)
      val result =
          ScriptRunnerUtil.getProcessOutput(
              commandLine, ScriptRunnerUtil.STDOUT_OR_STDERR_OUTPUT_KEY_FILTER, 100000000)
      SwingUtilities.invokeLater { callback.accept(result) }
    } catch (e: ExecutionException) {
      InfracostNotificationGroup.notifyError(project, e.localizedMessage)
    }
  }
}
