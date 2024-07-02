package io.infracost.plugins.infracost.listeners

import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.infracost.plugins.infracost.actions.RunInfracostAction

class InfracostFileListener : BulkFileListener {

    // This method is called after the files are processed
    // at the moment it will be all files in the workspace that have been updated
    // going forward we might want to check if the file is a terraform file explicitly
    override fun after(events: MutableList<out VFileEvent>) {
        for (event in events) {
            if (event.isFromSave) {
                val project = ProjectLocator.getInstance().guessProjectForFile(event.file!!) ?: return
                RunInfracostAction.runInfracost(project)
            }
        }
    }
}
