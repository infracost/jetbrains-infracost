package io.infracost.plugins.infracost.codeinsight

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.CodeVisionState.Companion.READY_EMPTY
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import io.infracost.plugins.infracost.actions.ResultProcessor
import io.infracost.plugins.infracost.icons.InfracostIcons

@Suppress("UnstableApiUsage")
class InfracostCodeVisionProvider : CodeVisionProvider<Unit> {
  override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
    val document =
        FileEditorManager.getInstance(editor.project!!).selectedTextEditor?.document
            ?: return READY_EMPTY
    val file =
        FileEditorManager.getInstance(editor.project!!).selectedTextEditor?.virtualFile
            ?: return READY_EMPTY
    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    return ReadAction.compute<CodeVisionState, RuntimeException> {
      val model = ResultProcessor.model ?: return@compute READY_EMPTY
      for (project in model.projects) {
        for (f in project.files) {
          if (f.filename == file.toNioPath().toString()) {
            for (r in f.resources) {
              val codeVisionText = String.format("Monthly cost: $%.2f", r.monthlyCost?.toFloat())
              val codeVision =
                  TextCodeVisionEntry(
                      codeVisionText,
                      id,
                      icon = InfracostIcons.Infracost,
                      codeVisionText,
                      codeVisionText,
                  )

              val offset = document.getLineEndOffset((r.metadata?.startLine ?: 0) - 1)
              val range = TextRange(offset, offset)
              lenses.add(range to codeVision)
            }
          }
        }
      }

      return@compute CodeVisionState.Ready(lenses)
    }
  }

  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Top

  override val id: String
    get() = "codevision.infracost"

  override val name: String
    get() = "io.infracost.vision"

  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()

  override fun precomputeOnUiThread(editor: Editor) {}
}

fun refresh(project: Project) {
  ApplicationManager.getApplication().invokeLater {
    WriteCommandAction.runWriteCommandAction(project) {
      val document = FileEditorManager.getInstance(project).selectedTextEditor?.document
      document?.insertString(0, " ")
      document?.deleteString(0, 1)
    }
  }
}
