package com.infracost.intellij

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager

class UpdateAction(
    private val project: Project,
    private val latestVersion: String,
) : NotificationAction("Update") {

  override fun actionPerformed(e: AnActionEvent, notification: Notification) {
    notification.expire()

    ProgressManager.getInstance()
        .run(
            object : Task.Backgroundable(project, "Updating Infracost Language Server...", true) {
              override fun run(indicator: ProgressIndicator) {
                try {
                  val servers =
                      LspServerManager.getInstance(project)
                          .getServersForProvider(InfracostLspServerSupportProvider::class.java)
                  val server =
                      servers.firstOrNull()
                          ?: throw IllegalStateException("Infracost LSP server not running")

                  server.sendRequestSync { (it as InfracostLanguageServer).update() }

                  ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater

                    LspServerManager.getInstance(project)
                        .stopAndRestartIfNeeded(InfracostLspServerSupportProvider::class.java)

                    notifyInfo(project, "Infracost Language Server updated to v$latestVersion.")
                  }
                } catch (ex: Exception) {
                  LOG.warn("Infracost update failed", ex)
                  ApplicationManager.getApplication().invokeLater {
                    if (project.isDisposed) return@invokeLater
                    notifyError(project, "Infracost update failed: ${ex.message}")
                  }
                }
              }
            }
        )
  }

  companion object {
    private val LOG = Logger.getInstance(UpdateAction::class.java)
  }
}
