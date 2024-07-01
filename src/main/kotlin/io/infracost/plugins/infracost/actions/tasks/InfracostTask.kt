package io.infracost.plugins.infracost.actions.tasks

import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import io.infracost.plugins.infracost.settings.InfracostSettingState
import java.io.File
import java.io.FileOutputStream
import java.util.*

abstract class InfracostTask(project: Project, taskTitle: String, cancellable: Boolean) :
    Backgroundable(project, taskTitle, cancellable), Runnable {
  companion object {
    @JvmStatic
    protected var binaryFile: String? = null

    fun ensureBinaryAvailable(): Boolean {
      if (this.binaryFile != null) {
        return true
      }
      if (InfracostSettingState.instance.infracostPath.isEmpty()) {
        ensureBinaryFile()
        return this.binaryFile != null
      }

      return findFileInPath(InfracostSettingState.instance.infracostPath) != null
    }


    private fun findFileInPath(fileName: String): String? {
      // Get the PATH environment variable
      val pathEnv = System.getenv("PATH") ?: return null

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
      return null // File not found in any of the directories
    }

    private fun ensureBinaryFile() {
      val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
      val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())

      var resourcePath: String? = null
      if (osName.contains("win")) {
        resourcePath = "/binaries/windows/${arch}/infracost.exe"
      } else if (osName.contains("mac")) {
        resourcePath = "/binaries/macos/${arch}/infracost"
      } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
        resourcePath = "/binaries/linux/${arch}/infracost"
      }

      if (resourcePath != null) {
        val resource = this::class.java.getResourceAsStream(resourcePath)
        if (resource != null) {
          val file = File.createTempFile("infracost", "")
          file.deleteOnExit()
          val out = FileOutputStream(file)
          out.write(resource.readAllBytes())
          out.close()
          file.setExecutable(true)
          file.deleteOnExit()
          this.binaryFile = file.absolutePath
        }
      }
    }
  }
}
