package com.infracost.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.xmlb.XmlSerializerUtil
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

@State(
    name = "com.infracost.intellij.InfracostSettingsState",
    storages = [Storage("infracost.xml")],
)
class InfracostSettingsState : PersistentStateComponent<InfracostSettingsState?> {
  var serverPath: String = ""
  var runParamsCacheTTLSeconds: Int = 300
  var debugUIAddress: String = ""

  override fun getState(): InfracostSettingsState = this

  override fun loadState(state: InfracostSettingsState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    val instance: InfracostSettingsState
      get() = ApplicationManager.getApplication().getService(InfracostSettingsState::class.java)
  }
}

class InfracostSettingsConfigurable : Configurable {
  private var panel: JPanel? = null
  private var serverPathField: TextFieldWithBrowseButton? = null
  private var cacheTTLSpinner: JSpinner? = null
  private var debugUIField: JTextField? = null

  override fun getDisplayName(): String = "Infracost"

  override fun createComponent(): JComponent {
    serverPathField =
        TextFieldWithBrowseButton().apply {
          addBrowseFolderListener(
              "Infracost LSP Binary",
              "Path to infracost-ls binary. Leave empty to use PATH.",
              ProjectManager.getInstance().defaultProject,
              FileChooserDescriptorFactory.createSingleFileDescriptor(),
          )
        }

    cacheTTLSpinner = JSpinner(SpinnerNumberModel(300, 0, Int.MAX_VALUE, 1))

    debugUIField = JTextField()

    panel =
        FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Server path:"), serverPathField!!, 1, true)
            .addLabeledComponent(JBLabel("Cache TTL (seconds):"), cacheTTLSpinner!!, 1, false)
            .addLabeledComponent(JBLabel("Debug UI address:"), debugUIField!!, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

    return panel!!
  }

  override fun getPreferredFocusedComponent(): JComponent? = serverPathField

  override fun isModified(): Boolean {
    val settings = InfracostSettingsState.instance
    val path = serverPathField ?: return false
    val ttl = cacheTTLSpinner ?: return false
    val debug = debugUIField ?: return false
    return path.text != settings.serverPath ||
        (ttl.value as Int) != settings.runParamsCacheTTLSeconds ||
        debug.text != settings.debugUIAddress
  }

  override fun apply() {
    val settings = InfracostSettingsState.instance
    settings.serverPath = serverPathField?.text ?: return
    settings.runParamsCacheTTLSeconds = (cacheTTLSpinner?.value as? Int) ?: return
    settings.debugUIAddress = debugUIField?.text ?: return
  }

  override fun reset() {
    val settings = InfracostSettingsState.instance
    serverPathField?.text = settings.serverPath
    cacheTTLSpinner?.value = settings.runParamsCacheTTLSeconds
    debugUIField?.text = settings.debugUIAddress
  }

  override fun disposeUIResources() {
    panel = null
    serverPathField = null
    cacheTTLSpinner = null
    debugUIField = null
  }
}
