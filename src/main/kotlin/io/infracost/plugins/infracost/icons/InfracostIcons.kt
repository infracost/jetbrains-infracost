package io.infracost.plugins.infracost.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

interface InfracostIcons {
  companion object {
    val Infracost: Icon = IconLoader.getIcon("/icons/infracost.svg", InfracostIcons::class.java)
    val TerraformFile: Icon = IconLoader.getIcon("/icons/terraform.svg", InfracostIcons::class.java)
    val Cloud: Icon = IconLoader.getIcon("/icons/cloud.svg", InfracostIcons::class.java)
    val Spend: Icon = IconLoader.getIcon("/icons/cash.svg", InfracostIcons::class.java)
  }
}
