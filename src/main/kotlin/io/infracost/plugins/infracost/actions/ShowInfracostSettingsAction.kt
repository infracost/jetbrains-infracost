package io.infracost.plugins.infracost.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

const val INFRACOST_SETTINGS_ID = "Infracost: Settings"

class ShowInfracostSettingsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ShowSettingsUtil.getInstance().showSettingsDialog(project, INFRACOST_SETTINGS_ID)
    }
}
