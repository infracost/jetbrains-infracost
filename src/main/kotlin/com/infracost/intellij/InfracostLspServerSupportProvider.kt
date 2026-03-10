package com.infracost.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.eclipse.lsp4j.services.LanguageServer
import java.nio.file.Path

class InfracostLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter
    ) {
        if (InfracostLspServerDescriptor.isSupportedFile(file)) {
            serverStarter.ensureServerStarted(InfracostLspServerDescriptor(project))
        }
    }
}

class InfracostLspServerDescriptor(project: Project) :
    ProjectWideLspServerDescriptor(project, "Infracost") {

    override fun isSupportedFile(file: VirtualFile): Boolean =
        Companion.isSupportedFile(file)

    override fun createCommandLine(): GeneralCommandLine {
        val settings = InfracostSettingsState.instance
        val binary = settings.serverPath.ifBlank { bundledBinary("infracost-ls") ?: "infracost-ls" }
        return GeneralCommandLine(binary).apply {
            project.basePath?.let { withWorkDirectory(it) }
            withEnvironment("INFRACOST_RUN_PARAMS_CACHE_TTL_SECONDS", settings.runParamsCacheTTLSeconds.toString())

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

    companion object {
        private const val PLUGIN_ID = "io.infracost.plugins.jetbrains-infracost"
        private val CFN_PATTERNS = listOf("template", "cloudformation", "cfn", "stack", "infracost")

        private val PLUGIN_BINARIES = listOf(
            "infracost-parser-plugin",
            "infracost-provider-plugin-aws",
            "infracost-provider-plugin-azurerm",
            "infracost-provider-plugin-google",
        )

        private fun pluginBinDir(): Path? {
            val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID)) ?: return null
            return plugin.pluginPath.resolve("bin")
        }

        private fun bundledBinary(name: String): String? {
            val bin = pluginBinDir()?.resolve(name) ?: return null
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
