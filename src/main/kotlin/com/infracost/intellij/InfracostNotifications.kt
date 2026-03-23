package com.infracost.intellij

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

private const val GROUP_ID = "Infracost"

internal fun createInfoNotification(message: String): Notification =
    createNotification(message, NotificationType.INFORMATION)

fun notifyInfo(project: Project, message: String): Notification =
    createInfoNotification(message).also { it.notify(project) }

fun notifyError(project: Project, message: String): Notification =
    createNotification(message, NotificationType.ERROR).also { it.notify(project) }

private fun createNotification(message: String, type: NotificationType): Notification =
    NotificationGroupManager.getInstance()
        .getNotificationGroup(GROUP_ID)
        .createNotification(message, type)
