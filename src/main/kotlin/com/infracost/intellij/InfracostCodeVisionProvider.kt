package com.infracost.intellij

import com.google.gson.Gson
import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.CodeVisionState.Companion.READY_EMPTY
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.LspServerManager
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.TextDocumentIdentifier

@Suppress("UnstableApiUsage")
class InfracostCodeVisionProvider : CodeVisionProvider<Unit> {

  override val id: String = ID
  override val name: String = "Infracost"
  override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top
  override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

  override fun precomputeOnUiThread(editor: Editor) {}

  override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
    val project = editor.project ?: return READY_EMPTY
    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return READY_EMPTY
    if (!InfracostLspServerDescriptor.isSupportedFile(file)) return READY_EMPTY

    val servers =
        LspServerManager.getInstance(project)
            .getServersForProvider(InfracostLspServerSupportProvider::class.java)
    val server = servers.firstOrNull() ?: return CodeVisionState.NotReady

    val uri =
        try {
          file.toNioPath().toUri().toString()
        } catch (_: UnsupportedOperationException) {
          return READY_EMPTY
        }
    val params = CodeLensParams(TextDocumentIdentifier(uri))

    val lenses =
        try {
          server.sendRequestSync { it.textDocumentService.codeLens(params) }
        } catch (e: Exception) {
          LOG.warn("codeLens request failed", e)
          return CodeVisionState.NotReady
        } ?: return CodeVisionState.NotReady

    LOG.debug("codeLens returned ${lenses.size} lenses for $uri")

    // Document access needs a read action
    return ReadAction.compute<CodeVisionState, RuntimeException> {
      val document = editor.document
      val byLine = mutableMapOf<Int, MutableList<String>>()
      for (lens in lenses) {
        val line = lens.range.start.line
        val title = lens.command?.title ?: continue
        if (line < 0 || line >= document.lineCount) continue
        byLine.getOrPut(line) { mutableListOf() }.add(title)
      }

      val entries = byLine.map { (line, titles) ->
        val offset = document.getLineStartOffset(line)
        val text = titles.joinToString(" | ")
        val entry =
            ClickableTextCodeVisionEntry(
                text,
                id,
                { _, e -> handleClick(e, uri, line) },
            )
        TextRange(offset, offset) to entry
      }

      CodeVisionState.Ready(entries)
    }
  }

  private fun handleClick(editor: Editor, uri: String, line: Int) {
    val project = editor.project ?: return
    ApplicationManager.getApplication().executeOnPooledThread {
      val servers =
          LspServerManager.getInstance(project)
              .getServersForProvider(InfracostLspServerSupportProvider::class.java)
      val server = servers.firstOrNull() ?: return@executeOnPooledThread
      try {
        val response = server.sendRequestSync {
          (it as InfracostLanguageServer).resourceDetails(ResourceDetailsParams(uri, line))
        }
        val gson = Gson()
        val result = gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)
        InfracostToolWindowFactory.show(project, ResourceDetailsParams(uri, line), result)
      } catch (e: Exception) {
        LOG.warn("resourceDetails request failed", e)
      }
    }
  }

  companion object {
    const val ID = "infracost.codelens"
    private val LOG = Logger.getInstance(InfracostCodeVisionProvider::class.java)

    fun forceRefresh(project: Project) {
      ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@invokeLater
        val document = editor.document
        ApplicationManager.getApplication().runWriteAction {
          CommandProcessor.getInstance().runUndoTransparentAction {
            val len = document.textLength
            document.insertString(len, " ")
            document.deleteString(len, len + 1)
          }
        }
      }
    }
  }
}
