package io.infracost.plugins.infracost.ui

import io.infracost.plugins.infracost.icons.InfracostIcons
import io.infracost.plugins.infracost.model.File
import io.infracost.plugins.infracost.model.InfracostProject
import io.infracost.plugins.infracost.model.Resource
import java.awt.Color
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultTreeCellRenderer

internal class InfracostTreeRenderer : DefaultTreeCellRenderer() {
  companion object {
    private val TransparentColor = Color(0, 0, 0, 0)
  }

  override fun getTreeCellRendererComponent(
      tree: JTree,
      value: Any,
      sel: Boolean,
      expanded: Boolean,
      leaf: Boolean,
      row: Int,
      hasFocus: Boolean
  ): Component {
    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
    this.setBackgroundNonSelectionColor(TransparentColor)

      when (value) {
          is InfracostProject -> {
              this.toolTipText = value.name
              this.text = value.toString()
              this.icon = InfracostIcons.Cloud
          }

          is File -> {
              this.icon = InfracostIcons.TerraformFile
              this.text = value.toString()
              this.toolTipText = value.filename
          }

          is Resource -> {
              this.icon = InfracostIcons.Spend
              this.text = value.toString()
              this.toolTipText = value.name
          }
      }
    return this
  }
}
