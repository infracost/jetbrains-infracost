package com.infracost.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification

class InfracostLanguageClientImpl(
    private val project: Project,
    serverNotificationsHandler: LspServerNotificationsHandler,
) : Lsp4jClient(serverNotificationsHandler) {

    @JsonNotification("infracost/scanComplete")
    fun scanComplete() {
        InfracostCodeVisionProvider.forceRefresh(project)
        project.getUserData(InfracostToolWindowFactory.PANEL_KEY)?.refreshCurrentResource()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                WindowManager.getInstance().getStatusBar(project)?.info = ""
            }
        }
    }
}
