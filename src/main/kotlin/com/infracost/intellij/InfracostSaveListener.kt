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

    // Whether a Bicep file counts as supported depends on the project (its trust
    // state), and this listener is document-scoped, so ask every open project. The
    // notification below already goes to whichever project has a running server, so
    // "supported by any of them" is the same granularity the check has always had.
    val projects = ProjectManager.getInstance().openProjects.filter { !it.isDisposed }
    val supported =
        projects.any {
          InfracostLspServerDescriptor.isSupportedFile(
              vf, InfracostLspServerDescriptor.isBicepEnabled(it))
        }
    if (!supported) return

    val uri = vf.toNioPath().toUri().toString()
    val saveParams = DidSaveTextDocumentParams(TextDocumentIdentifier(uri), document.text)

    for (project in projects) {
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
