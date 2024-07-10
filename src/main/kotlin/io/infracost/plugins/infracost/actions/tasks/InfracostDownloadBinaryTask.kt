package io.infracost.plugins.infracost.actions.tasks

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.actions.CheckAuthAction
import io.infracost.plugins.infracost.binary.InfracostBinary
import io.infracost.plugins.infracost.binary.InfracostBinary.Companion.downloadBinary
import io.infracost.plugins.infracost.settings.InfracostSettingState
import java.io.File
import java.nio.file.Paths
import java.util.*
import javax.swing.SwingUtilities

internal class InfracostDownloadBinaryTask(private val project: Project, val initial: Boolean) :
    Backgroundable(project, "Downloading Infracost", false), Runnable {


    override fun run(indicator: ProgressIndicator) {
        this.run()
    }

    override fun run() {
        if (!initial) {
            // this is a force download
            getInfracost()
            return
        }

        if (InfracostSettingState.instance.infracostPath.isEmpty()) {
            getInfracost()
            return
        } else if (File(InfracostSettingState.instance.infracostPath).exists()) {
            InfracostBinary.binaryFile = InfracostSettingState.instance.infracostPath
        } else {
            InfracostBinary.binaryFile = findFileInPath(InfracostSettingState.instance.infracostPath)
            if (InfracostBinary.binaryFile.isEmpty()) {
                getInfracost()
                return
            }
        }

        SwingUtilities.invokeLater {
            CheckAuthAction.checkAuth(project)
        }

    }

    private fun findFileInPath(fileName: String): String {
        // Get the PATH environment variable
        val pathEnv = System.getenv("PATH") ?: return ""

        // Split the PATH into individual directories
        val paths = pathEnv.split(File.pathSeparator)

        // Iterate over each directory
        for (path in paths) {
            val file = File(path, fileName)
            // Check if the file exists in this directory
            if (file.exists() && file.isFile) {
                return file.absolutePath
            }
        }
        return ""
    }

    private fun getInfracost() {

        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        var arch = System.getProperty("os.arch").lowercase(Locale.getDefault())
        if (arch == "aarch64") {
            arch = "arm64"
        }

        var binaryTarget = ""
        var binaryRelease = ""
        if (osName.contains("win")) {
            binaryRelease = "windows-${arch}"
            binaryTarget = "infracost.exe"
        } else if (osName.contains("mac")) {
            binaryRelease = "darwin-${arch}"
            binaryTarget = "infracost"
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            binaryRelease = "linux-${arch}"
            binaryTarget = "infracost"
        }

        PluginManagerCore.getPlugin(PluginId.getId("io.infracost.plugins.jetbrains-infracost"))?.pluginPath?.let {
            val targetFile = Paths.get(it.toAbsolutePath().toString(), binaryTarget).toFile()
            if (downloadBinary(project, binaryRelease, targetFile, initial)) {
                InfracostSettingState.instance.infracostPath = targetFile.absolutePath
                InfracostBinary.binaryFile = targetFile.absolutePath

                SwingUtilities.invokeLater {
                    CheckAuthAction.checkAuth(project)
                }
                
            }
        }
    }
}