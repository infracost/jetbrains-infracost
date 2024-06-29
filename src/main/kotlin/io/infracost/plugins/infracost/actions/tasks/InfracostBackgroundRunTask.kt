package io.infracost.plugins.infracost.actions.tasks

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.ui.notify.InfracostNotificationGroup
import java.io.File
import java.util.function.BiConsumer
import javax.swing.SwingUtilities

internal class InfracostBackgroundRunTask(
    private val project: Project,
    private val resultFile: File,
    private val callback: BiConsumer<Project, File>
) : InfracostTask(project, "Running Infracost", false), Runnable {
  override fun run(indicator: ProgressIndicator) {
    this.run()
  }

  override fun run() {
    if (!ensureBinaryAvailable()) {
      InfracostNotificationGroup.notifyError(project, "Infracost binary not found")
      return
    }
    val commandParts: MutableList<String?> = ArrayList()
    commandParts.add(binaryFile)
    commandParts.add("breakdown")
    commandParts.add("--format=json")
    commandParts.add(String.format("--path=%s", project.basePath))
    commandParts.add(String.format("--out-file=%s", resultFile.absolutePath))

    val commandLine =
        GeneralCommandLine(commandParts)
            .withEnvironment(
                mapOf(
                    "INFRACOST_SKIP_UPDATE_CHECK" to "true",
                    "INFRACOST_GRAPH_EVALUATOR" to "true",
                    "INFRACOST_NO_COLOR" to "true"))

    val process: Process
    try {
      process = commandLine.createProcess()
    } catch (e: ExecutionException) {
      InfracostNotificationGroup.notifyError(project, e.localizedMessage)
      return
    }

    val handler = OSProcessHandler(process, commandLine.commandLineString)
    try {
      ScriptRunnerUtil.getProcessOutput(
          handler, ScriptRunnerUtil.STDOUT_OR_STDERR_OUTPUT_KEY_FILTER, 100000000)
      InfracostNotificationGroup.notifyInformation(
          project, "infracost run completed, updating results")
      SwingUtilities.invokeLater { callback.accept(this.project, this.resultFile) }
    } catch (e: ExecutionException) {
      InfracostNotificationGroup.notifyError(project, e.localizedMessage)
    }
  }
}
