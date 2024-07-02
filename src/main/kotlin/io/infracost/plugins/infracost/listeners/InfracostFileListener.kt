package io.infracost.plugins.infracost.listeners

import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.infracost.plugins.infracost.actions.RunInfracostAction

val INFRACOST_FILE_EXTENSIONS = setOf("tf", "hcl", "tfvars")
val INFRACOST_FILES = setOf("infracost.yml", "infracost.yml.tmpl", "infracost-usage.yml")

class InfracostFileListener : BulkFileListener {

    // This method is called after the files are processed
    // at the moment it will be all files in the workspace that have been updated
    // going forward we might want to check if the file is a terraform file explicitly
    override fun after(events: MutableList<out VFileEvent>) {
        for (event in events) {
            if (event.isFromSave) {
                if (event.file?.extension?.lowercase() in INFRACOST_FILE_EXTENSIONS ||
                        INFRACOST_FILES.contains(event.file?.name?.lowercase())){
                    val project = ProjectLocator.getInstance().guessProjectForFile(event.file!!) ?: return
                    RunInfracostAction.runInfracost(project)
                }
            }
        }
    }
}
