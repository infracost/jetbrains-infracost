package com.infracost.intellij

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
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

    override fun computeForEditor(
        editor: Editor,
        file: PsiFile,
    ): List<Pair<TextRange, CodeVisionEntry>> {
        if (file.virtualFile?.extension != "tf") return emptyList()

        val project = editor.project ?: return emptyList()
        val servers = LspServerManager.getInstance(project)
            .getServersForProvider(InfracostLspServerSupportProvider::class.java)
        val server = servers.firstOrNull() ?: return emptyList()

        val uri = file.virtualFile.url
        val params = CodeLensParams(TextDocumentIdentifier(uri))

        val lenses = try {
            server.lsp4jServer.textDocumentService.codeLens(params).get(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()

        val document = editor.document

        // Group lenses by line and combine into a single entry per line,
        // since CodeVision shows one entry per TextRange per provider.
        val byLine = mutableMapOf<Int, MutableList<String>>()
        for (lens in lenses) {
            val line = lens.range.start.line
            val title = lens.command?.title ?: continue
            if (line < 0 || line >= document.lineCount) continue
            byLine.getOrPut(line) { mutableListOf() }.add(title)
        }

        return byLine.map { (line, titles) ->
            val offset = document.getLineStartOffset(line)
            TextRange(offset, offset) to TextCodeVisionEntry(titles.joinToString(" | "), id)
        }
    }

    companion object {
        const val ID = "infracost.codelens"
    }
}
