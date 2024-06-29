package io.infracost.plugins.infracost.actions

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import io.infracost.plugins.infracost.codeinsight.refresh
import io.infracost.plugins.infracost.model.InfracostModel
import io.infracost.plugins.infracost.model.Results
import io.infracost.plugins.infracost.ui.InfracostWindow
import io.infracost.plugins.infracost.ui.TOOL_WINDOW_ID
import io.infracost.plugins.infracost.ui.notify.InfracostNotificationGroup
import java.io.File
import java.io.IOException

/**
 * ResultProcessor takes the results finding and unmarshalls to object Then updates the findings
 * window
 */
object ResultProcessor {

  var model: InfracostModel? = null
    private set

  fun updateResults(project: Project?, resultFile: File?) {
    try {
      val resultsMapper = ObjectMapper()
      resultsMapper.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
      resultsMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      val results = resultsMapper.readValue(resultFile, Results::class.java)
      this.model = InfracostModel(results)
    } catch (e: IOException) {
      InfracostNotificationGroup.notifyError(
          project, String.format("Failed to deserialize the results file. %s", e.localizedMessage))
      return
    } finally {
      resultFile?.deleteOnExit()
    }

    // redraw the explorer with the updated content
    val toolWindow = ToolWindowManager.getInstance(project!!).getToolWindow(TOOL_WINDOW_ID)
    val content = toolWindow!!.contentManager.getContent(0)
    (content!!.component as InfracostWindow).apply {
      refreshModel()
      redraw()
      refresh(project)
    }
  }
}
