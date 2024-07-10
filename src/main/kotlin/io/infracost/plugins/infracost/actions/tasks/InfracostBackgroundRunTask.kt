package io.infracost.plugins.infracost.actions.tasks

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.binary.InfracostBinary
import io.infracost.plugins.infracost.ui.notify.InfracostNotificationGroup
import java.io.File
import java.nio.file.Paths
import java.util.function.BiConsumer
import javax.swing.SwingUtilities

internal class InfracostBackgroundRunTask(
    private val project: Project,
    private val resultFile: File,
    private val callback: BiConsumer<Project, File>
) : Backgroundable(project, "Running Infracost", false), Runnable {
    override fun run(indicator: ProgressIndicator) {
        this.run()
    }


    override fun run() {
        val infracostConfigPath = Paths.get(project.basePath, "infracost.yml")
        val infracostConfigTemplathPath = Paths.get(project.basePath, "infracost.yml.tmpl")
        val infracostUsageFilePath = Paths.get(project.basePath, "infracost-usage.yml")

        val commandParts: MutableList<String?> = ArrayList()
        commandParts.add(InfracostBinary.binaryFile)
        commandParts.add("breakdown")
        commandParts.add("--format=json")
        commandParts.add(String.format("--out-file=%s", resultFile.absolutePath))

        if (infracostUsageFilePath.toFile().exists()) {
            commandParts.add(String.format("--usage-file=%s", infracostUsageFilePath.toAbsolutePath()))
            commandParts.add(String.format("--path=%s", project.basePath))
        } else {
            if (infracostConfigPath.toFile().exists()) {
                commandParts.add(String.format("--config-file=%s", infracostConfigPath.toAbsolutePath()))
            } else if (infracostConfigTemplathPath.toFile().exists()) {
                commandParts.add(String.format("--template-file=%s", infracostConfigTemplathPath.toAbsolutePath()))
            } else {
                commandParts.add(String.format("--path=%s", project.basePath))
            }
        }

        val command = ProcessBuilder(commandParts)
        command.environment().set("INFRACOST_CLI_PLATFORM", "jetbrains")
        command.environment().set("INFRACOST_SKIP_UPDATE_CHECK", "true")
        command.environment().set("INFRACOST_GRAPH_EVALUATOR", "true")
        command.environment().set("INFRACOST_NO_COLOR", "true")
        command.directory(File(project.basePath.toString()))
        val process: Process
        try {
            process = command.start()
        } catch (e: ExecutionException) {
            InfracostNotificationGroup.notifyError(project, e.localizedMessage)
            return
        }

        val handler = OSProcessHandler(process, command.toString())
        try {
            ScriptRunnerUtil.getProcessOutput(
                handler, ScriptRunnerUtil.STDOUT_OR_STDERR_OUTPUT_KEY_FILTER, 100000000
            )
            SwingUtilities.invokeLater { callback.accept(this.project, this.resultFile) }
        } catch (e: ExecutionException) {
            InfracostNotificationGroup.notifyError(project, e.localizedMessage)
        }
    }
}
