package io.infracost.plugins.infracost.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import io.infracost.plugins.infracost.actions.DownloadInfracostAction
import io.infracost.plugins.infracost.binary.InfracostBinary
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Supports creating and managing a [JPanel] for the Settings Dialog. */
class InfracostSettingsComponent {
    val panel: JPanel
    private val infracostPath = TextFieldWithBrowseButton()
    private val updateButton = JButton("Update Bundled Infracost")
    private val explicitInfracost = JBCheckBox("Don't run Infracost on save")

    init {
        infracostPath.addBrowseFolderListener(
            "Infracost binary path",
            "Set the explicit path to infracost",
            ProjectManager.getInstance().defaultProject,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        updateButton.toolTipText = "Update the bundled version of Infracost. This won't update any additional install you might have."
        updateButton.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                SwingUtilities.invokeLater {
                    DownloadInfracostAction.runDownload(ProjectManager.getInstance().defaultProject, false)
                }
            }
        })

        panel =
            FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Specific infracost path: "), infracostPath, 1, true)
                .addVerticalGap(5)
                .addComponent(explicitInfracost, 1)
                .addVerticalGap(5)
                .addComponent(updateButton)
                .addComponentFillVertically(JPanel(), 0)
                .panel
    }

    val preferredFocusedComponent: JComponent
        get() = infracostPath

    fun getInfracostPath(): String {
        return infracostPath.text
    }

    fun setInfracostPath(newText: String) {
        infracostPath.text = newText
        InfracostBinary.binaryFile = newText
    }

    fun getExplicitInfracost(): Boolean {
        return explicitInfracost.isSelected
    }

    fun setExplicitInfracost(newVal: Boolean) {
        explicitInfracost.isSelected = newVal
    }
}
