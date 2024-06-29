package io.infracost.plugins.infracost.ui.notify

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object InfracostNotificationGroup {
  fun notifyError(project: Project?, content: String) {
    notify(project, content, NotificationType.ERROR)
  }

    fun notifyInformation(project: Project?, content: String) {
    notify(project, content, NotificationType.INFORMATION)
  }

  private fun notify(project: Project?, content: String, notificationType: NotificationType) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Infracost Notifications")
        .createNotification(content, notificationType)
        .notify(project)
  }
}
