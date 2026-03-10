package com.infracost.intellij

import com.google.gson.Gson
import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.util.concurrent.TimeUnit

@Suppress("UnstableApiUsage")
class InfracostCodeVisionProvider : DaemonBoundCodeVisionProvider {

    override val id: String = ID
    override val name: String = "Infracost"
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Top
    override val relativeOrderings: List<CodeVisionRelativeOrdering> = emptyList()

    // computeForEditor is called off-EDT by the DaemonBoundCodeVisionProvider contract
    override fun computeForEditor(
        editor: Editor,
        file: PsiFile,
    ): List<Pair<TextRange, CodeVisionEntry>> {
        val vf = file.virtualFile ?: return emptyList()
        if (!InfracostLspServerDescriptor.isSupportedFile(vf)) return emptyList()

        val project = editor.project ?: return emptyList()
        val servers = LspServerManager.getInstance(project)
            .getServersForProvider(InfracostLspServerSupportProvider::class.java)
        val server = servers.firstOrNull() ?: return emptyList()

        val uri = vf.url
        val params = CodeLensParams(TextDocumentIdentifier(uri))

        val lenses = try {
            server.lsp4jServer.textDocumentService.codeLens(params).get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()

        val document = editor.document

        val byLine = mutableMapOf<Int, MutableList<String>>()
        for (lens in lenses) {
            val line = lens.range.start.line
            val title = lens.command?.title ?: continue
            if (line < 0 || line >= document.lineCount) continue
            byLine.getOrPut(line) { mutableListOf() }.add(title)
        }

        return byLine.map { (line, titles) ->
            val offset = document.getLineStartOffset(line)
            val entry = ClickableTextCodeVisionEntry(
                titles.joinToString(" | "),
                id,
                { _, e -> handleClick(e, uri, line) },
            )
            TextRange(offset, offset) to entry
        }
    }

    private fun handleClick(editor: Editor, uri: String, line: Int) {
        val project = editor.project ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val servers = LspServerManager.getInstance(project)
                .getServersForProvider(InfracostLspServerSupportProvider::class.java)
            val server = servers.firstOrNull() ?: return@executeOnPooledThread
            val lsp = server.lsp4jServer as? InfracostLanguageServer ?: return@executeOnPooledThread

            try {
                val response = lsp.resourceDetails(ResourceDetailsParams(uri, line))
                    .get(5, TimeUnit.SECONDS)
                val gson = Gson()
                val result = gson.fromJson(gson.toJson(response), ResourceDetailsResult::class.java)
                InfracostToolWindowFactory.show(project, result)
            } catch (_: Exception) {
                // Ignore errors
            }
        }
    }

    companion object {
        const val ID = "infracost.codelens"
    }
}
