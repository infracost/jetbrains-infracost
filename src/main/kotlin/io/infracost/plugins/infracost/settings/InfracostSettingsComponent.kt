package io.infracost.plugins.infracost.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import io.infracost.plugins.infracost.actions.tasks.InfracostTask
import javax.swing.JComponent
import javax.swing.JPanel

/** Supports creating and managing a [JPanel] for the Settings Dialog. */
class InfracostSettingsComponent {
    val panel: JPanel
    private val infracostPath = TextFieldWithBrowseButton()

    init {
        infracostPath.addBrowseFolderListener(
            "Infracost binary path",
            "Set the explicit path to infracost",
            ProjectManager.getInstance().defaultProject,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        panel =
            FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Specific infracost path: "), infracostPath, 1, true)
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
        InfracostTask.resetBinaryFile()
    }
}
