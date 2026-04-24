package com.infracost.intellij

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class InfracostLogoutAction : AnAction("Infracost: Logout") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val panel = project.getUserData(InfracostToolWindowFactory.PANEL_KEY) ?: return
    panel.performLogout()
  }
}

class InfracostSwitchOrgAction : AnAction("Infracost: Switch Organization") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val panel = project.getUserData(InfracostToolWindowFactory.PANEL_KEY) ?: return
    panel.showOrgSelector()
  }
}

class InfracostScanAction :
    AnAction("Scan Workspace", "Run Infracost scan on the workspace", AllIcons.Actions.Execute) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val panel = project.getUserData(InfracostToolWindowFactory.PANEL_KEY) ?: return
    panel.triggerScan()
  }
}
