package io.infracost.plugins.infracost.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "io.infracost.plugins.infracost.settings.AppSettingsState",
    storages = [Storage("infracost.xml")])
class InfracostSettingState : PersistentStateComponent<InfracostSettingState?> {
  var infracostPath: String = ""

  override fun getState(): InfracostSettingState {
    return this
  }

  override fun loadState(state: InfracostSettingState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    val instance: InfracostSettingState
      get() = ApplicationManager.getApplication().getService(InfracostSettingState::class.java)
  }
}
