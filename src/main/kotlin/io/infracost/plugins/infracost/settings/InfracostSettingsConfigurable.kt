package io.infracost.plugins.infracost.settings

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/** Provides controller functionality for application settings. */
class InfracostSettingsConfigurable : Configurable {
    private var infracostSettingsComponent: InfracostSettingsComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    @Override
    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
        return "Infracost: Settings"
    }

    @Override
    override fun getPreferredFocusedComponent(): JComponent {
        return infracostSettingsComponent!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        infracostSettingsComponent = InfracostSettingsComponent()
        return infracostSettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val settings = InfracostSettingState.instance
        val modified = infracostSettingsComponent!!.getInfracostPath() != settings.infracostPath
        return modified
    }

    override fun apply() {
        val settings = InfracostSettingState.instance
        settings.infracostPath = infracostSettingsComponent!!.getInfracostPath()
    }

    override fun reset() {
        val settings = InfracostSettingState.instance
        infracostSettingsComponent!!.setInfracostPath(settings.infracostPath)
    }

    override fun disposeUIResources() {
        infracostSettingsComponent = null
    }
}
