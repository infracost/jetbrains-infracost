package com.infracost.intellij

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.lsp.api.LspServerManager
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier

class InfracostSaveListener : FileDocumentManagerListener {
  override fun beforeDocumentSaving(document: Document) {
    val vf = FileDocumentManager.getInstance().getFile(document) ?: return
    if (!InfracostLspServerDescriptor.isSupportedFile(vf)) return

    val uri = vf.toNioPath().toUri().toString()
    val saveParams = DidSaveTextDocumentParams(TextDocumentIdentifier(uri), document.text)

    for (project in ProjectManager.getInstance().openProjects) {
      if (project.isDisposed) continue
      @Suppress("UnstableApiUsage")
      val servers =
          LspServerManager.getInstance(project)
              .getServersForProvider(InfracostLspServerSupportProvider::class.java)
      val server = servers.firstOrNull() ?: continue
      server.sendNotification { it.textDocumentService.didSave(saveParams) }

      InfracostStatusBarWidget.getInstance(project)?.show("Infracost: Scanning...")
      break
    }
  }
}
