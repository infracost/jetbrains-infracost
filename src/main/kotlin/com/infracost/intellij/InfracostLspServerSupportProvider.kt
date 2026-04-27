package com.infracost.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import java.nio.file.Path
import org.eclipse.lsp4j.services.LanguageServer

class InfracostLspServerSupportProvider : LspServerSupportProvider {
  private val descriptors = com.intellij.util.containers.ContainerUtil.createConcurrentWeakMap<Project, InfracostLspServerDescriptor>()

  override fun fileOpened(
      project: Project,
      file: VirtualFile,
      serverStarter: LspServerSupportProvider.LspServerStarter,
  ) {
    if (InfracostLspServerDescriptor.isSupportedFile(file)) {
      val desc = descriptors.getOrPut(project) { InfracostLspServerDescriptor(project) }
      serverStarter.ensureServerStarted(desc)
    }
  }
}

class InfracostLspServerDescriptor(project: Project) :
    ProjectWideLspServerDescriptor(project, "Infracost") {

  override fun isSupportedFile(file: VirtualFile): Boolean = Companion.isSupportedFile(file)

  override fun createInitializationOptions(): Any {
    val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))
    val pluginVersion = plugin?.version ?: "unknown"
    return mapOf(
        "clientName" to "jetbrains",
        "extensionVersion" to pluginVersion,
        "supportsCodeLens" to true,
    )
  }

  override fun createCommandLine(): GeneralCommandLine {
    val settings = InfracostSettingsState.instance
    val binary = settings.serverPath.ifBlank { bundledBinary("infracost-ls") ?: "infracost-ls" }
    return GeneralCommandLine(binary).apply {
      project.basePath?.let { withWorkDirectory(it) }
      withEnvironment(
          "INFRACOST_RUN_PARAMS_CACHE_TTL_SECONDS",
          settings.runParamsCacheTTLSeconds.toString(),
      )

      withEnvironment("INFRACOST_ENABLE_DIAGNOSTICS", settings.enableDiagnostics.toString())

      if (settings.debug) {
        withEnvironment("INFRACOST_DEBUG", "true")
      }

      if (settings.traceLevel != "off") {
        withEnvironment("INFRACOST_TRACE_LEVEL", settings.traceLevel)
      }

      if (settings.debugUIAddress.isNotBlank()) {
        withEnvironment("INFRACOST_DEBUG_UI", settings.debugUIAddress)
      }

      for (name in PLUGIN_BINARIES) {
        val path = bundledBinary(name)
        if (path != null) {
          withEnvironment(envVarName(name), path)
        }
      }
    }
  }

  override val lsp4jServerClass: Class<out LanguageServer>
    get() = InfracostLanguageServer::class.java

  override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient {
    return InfracostLanguageClientImpl(project, handler)
  }

  companion object {
    private const val PLUGIN_ID = "io.infracost.plugins.jetbrains-infracost"
    private val CFN_PATTERNS = listOf("template", "cloudformation", "cfn", "stack", "infracost")

    private val PLUGIN_BINARIES =
        listOf(
            "infracost-parser-plugin",
            "infracost-provider-plugin-aws",
            "infracost-provider-plugin-azurerm",
            "infracost-provider-plugin-google",
        )

    private fun pluginBinDir(): Path? {
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: return null
      return plugin.pluginPath.resolve("bin")
    }

    private val IS_WINDOWS = System.getProperty("os.name")?.lowercase()?.contains("win") == true

    private val PLATFORM_SUFFIX: String by lazy {
      val os =
          when {
            IS_WINDOWS -> "windows"
            System.getProperty("os.name")?.lowercase()?.contains("mac") == true -> "darwin"
            else -> "linux"
          }
      val arch =
          when (System.getProperty("os.arch")) {
            "aarch64",
            "arm64" -> "arm64"
            else -> "amd64"
          }
      "$os-$arch"
    }

    private fun bundledBinary(name: String): String? {
      val dir = pluginBinDir() ?: return null
      val suffix = if (IS_WINDOWS) ".exe" else ""
      // Try platform-specific binary first, then fall back to plain name
      val platformBin = dir.resolve("$name-$PLATFORM_SUFFIX$suffix")
      if (platformBin.toFile().isFile) return platformBin.toString()
      val bin = dir.resolve("$name$suffix")
      return if (bin.toFile().isFile) bin.toString() else null
    }

    private fun envVarName(binaryName: String): String =
        binaryName.uppercase().replace('-', '_') + "_PATH"

    fun isSupportedFile(file: VirtualFile): Boolean {
      if (file.extension == "tf") return true

      val name = file.name.lowercase()
      if (name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json")) {
        return CFN_PATTERNS.any { name.contains(it) }
      }
      return false
    }
  }
}
