package com.infracost.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor

class InfracostLspServerSupportProvider : LspServerSupportProvider {
    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter
    ) {
        if (file.extension == "tf") {
            serverStarter.ensureServerStarted(InfracostLspServerDescriptor(project))
        }
    }

    private class InfracostLspServerDescriptor(project: Project) :
        ProjectWideLspServerDescriptor(project, "Infracost") {

        override fun isSupportedFile(file: VirtualFile): Boolean =
            file.extension == "tf"

        override fun createCommandLine(): GeneralCommandLine =
            GeneralCommandLine("infracost-ls").apply {
                project.basePath?.let { withWorkDirectory(it) }
            }
    }
}
