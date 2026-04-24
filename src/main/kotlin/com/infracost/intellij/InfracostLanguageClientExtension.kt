package com.infracost.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification

data class UpdateAvailableParams(
    val updateAvailable: Boolean = false,
    val latestVersion: String = "",
    val currentVersion: String = "",
)

class InfracostLanguageClientImpl(
    private val project: Project,
    serverNotificationsHandler: LspServerNotificationsHandler,
) : Lsp4jClient(serverNotificationsHandler) {

  @JsonNotification("infracost/loginComplete")
  fun loginComplete() {
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      val panel = project.getUserData(InfracostToolWindowFactory.PANEL_KEY)
      panel?.onLoginComplete()
    }
  }

  @JsonNotification("infracost/logoutComplete")
  fun logoutComplete() {
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      val panel = project.getUserData(InfracostToolWindowFactory.PANEL_KEY)
      panel?.onLogoutComplete()
    }
  }

  @JsonNotification("infracost/scanComplete")
  fun scanComplete() {
    InfracostCodeVisionProvider.forceRefresh(project)
    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      val panel = project.getUserData(InfracostToolWindowFactory.PANEL_KEY)
      panel?.fetchGuardrails()
      panel?.fetchOrgs()
      panel?.fetchWorkspaceSummary()
      panel?.refreshCurrentResource()
      panel?.refreshEmpty()
      InfracostStatusBarWidget.getInstance(project)?.clear()
    }
  }

  @JsonNotification("infracost/updateAvailable")
  fun updateAvailable(params: UpdateAvailableParams) {
    if (!params.updateAvailable) return

    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater

      createInfoNotification(
              "Infracost Language Server update available: <nobr>v${params.currentVersion}</nobr> → <nobr>v${params.latestVersion}</nobr>"
          )
          .apply { addAction(UpdateAction(project, params.latestVersion)) }
          .notify(project)
    }
  }
}
