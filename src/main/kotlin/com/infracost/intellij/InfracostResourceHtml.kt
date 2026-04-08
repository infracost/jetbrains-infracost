package com.infracost.intellij

import com.google.gson.annotations.SerializedName
import com.intellij.ui.JBColor
import java.awt.Color
import javax.swing.UIManager

data class CostComponentDetail(
    val name: String,
    val unit: String,
    val price: String,
    val monthlyQuantity: String,
    val monthlyCost: String,
)

data class PolicyDetail(
    val risk: String?,
    val effort: String?,
    val downtime: String?,
    val riskDescription: String?,
    val effortDescription: String?,
    val downtimeDescription: String?,
    val additionalDetails: String?,
    val shortTitle: String?,
)

data class ViolationDetail(
    val policyName: String,
    val policySlug: String,
    val message: String,
    val attribute: String?,
    val blockPullRequest: Boolean,
    val monthlySavings: String?,
    val savingsDetails: String?,
    val policyDetail: PolicyDetail?,
)

data class TagViolationDetail(
    val policyName: String,
    @SerializedName("blockPR") val blockPR: Boolean,
    val message: String,
    val policyMessage: String?,
    val missingTags: List<String>?,
    val invalidTags: List<InvalidTag>?,
)

data class InvalidTag(
    val key: String,
    val value: String,
    val suggestion: String?,
    val message: String?,
    val validValues: List<String>?,
)

data class ResourceDetail(
    val name: String,
    val type: String,
    val monthlyCost: String,
    val costComponents: List<CostComponentDetail>?,
    val violations: List<ViolationDetail>?,
    val tagViolations: List<TagViolationDetail>?,
)

data class ResourceDetailsResult(
    val resource: ResourceDetail?,
    val scanning: Boolean,
    val needsLogin: Boolean?,
)

data class StatusInfo(
    val version: String? = null,
    val workspaceRoot: String? = null,
    val loggedIn: Boolean? = false,
    val scanning: Boolean? = false,
    val projectCount: Int? = 0,
    val projectNames: List<String>? = null,
    val resourceCount: Int? = 0,
    val violationCount: Int? = 0,
    val tagIssueCount: Int? = 0,
    val configFound: Boolean? = false,
)

data class FileSummaryResource(
    val name: String = "",
    val line: Int = 0,
    val monthlyCost: String = "",
    val policyIssues: Int = 0,
    val tagIssues: Int = 0,
)

data class FileSummaryResult(
    val resources: List<FileSummaryResource>?,
)

data class ThemeColors(
    val bg: String,
    val fg: String,
    val fgDim: String,
    val border: String,
    val green: String,
    val errorBg: String,
    val errorFg: String,
    val warning: String,
    val link: String,
    val codeBg: String,
    val cardBg: String,
    val btnBg: String,
    val btnFg: String,
    val btnHover: String,
) {
  companion object {
    fun fromIde(): ThemeColors {
      fun ui(key: String): Color? = UIManager.getColor(key)

      val bg = ui("Panel.background") ?: JBColor.PanelBackground
      val fg = ui("Panel.foreground") ?: JBColor.foreground()
      val border = ui("Component.borderColor") ?: ui("Separator.separatorColor") ?: JBColor.border()
      val link = ui("Link.activeForeground") ?: ui("link.foreground") ?: JBColor.BLUE
      val btnBg = ui("Button.default.startBackground") ?: ui("Button.startBackground") ?: link
      val btnHover = ui("Button.default.focusedBorderColor") ?: btnBg.darker()

      val isDark = !JBColor.isBright()

      return ThemeColors(
          bg = bg.css(),
          fg = fg.css(),
          fgDim = (ui("Component.infoForeground") ?: fg.blend(bg, 0.4)).css(),
          border = border.css(),
          green = (if (isDark) Color(78, 201, 176) else Color(0, 128, 0)).css(),
          errorBg =
              (ui("ValidationTooltip.errorBackground")
                      ?: if (isDark) Color(90, 29, 29) else Color(248, 215, 218))
                  .css(),
          errorFg =
              (ui("ERRORS_ATTRIBUTES.FOREGROUND")
                      ?: if (isDark) Color(244, 135, 113) else Color(190, 17, 0))
                  .css(),
          warning =
              (ui("WARNING_ATTRIBUTES.FOREGROUND")
                      ?: if (isDark) Color(204, 167, 0) else Color(191, 136, 3))
                  .css(),
          link = link.css(),
          codeBg = (ui("EditorPane.background") ?: bg.shift(if (isDark) 1.1f else 0.96f)).css(),
          cardBg =
              (ui("Table.stripeColor")
                      ?: ui("EditorPane.inactiveBackground")
                      ?: bg.shift(if (isDark) 1.1f else 0.96f))
                  .css(),
          btnBg = btnBg.css(),
          btnFg = (ui("Button.default.foreground") ?: Color.WHITE).css(),
          btnHover = btnHover.css(),
      )
    }

    private fun Color.css(): String = "rgb($red,$green,$blue)"

    private fun Color.blend(other: Color, fraction: Double): Color =
        Color(
            (red + (other.red - red) * fraction).toInt().coerceIn(0, 255),
            (green + (other.green - green) * fraction).toInt().coerceIn(0, 255),
            (blue + (other.blue - blue) * fraction).toInt().coerceIn(0, 255),
        )

    private fun Color.shift(factor: Float): Color =
        Color(
            (red * factor).toInt().coerceIn(0, 255),
            (green * factor).toInt().coerceIn(0, 255),
            (blue * factor).toInt().coerceIn(0, 255),
        )
  }
}

object InfracostResourceHtml {

  /** Wraps sendCommand output with HTML attribute escaping for safe use in onclick="...". */
  private fun cmd(sendCommand: (String) -> String, command: String): String =
      escAttr(sendCommand(command))

  fun renderLogin(sendCommand: (String) -> String): String =
      renderPage(
          """
        <div class="state">
          <p>Login to Infracost to see cloud costs, FinOps policies, and tag issues.</p>
          <button class="login-btn" onclick="${cmd(sendCommand, "login")}">Login to Infracost</button>
        </div>
        <div class="empty-links">
          <a href="#" onclick="${cmd(sendCommand, "troubleshoot")}; return false;">Troubleshooting</a>
          <a href="https://infracost.io/community-chat">Join the Slack</a>
          <a href="https://github.com/infracost/infracost/discussions">Raise an issue</a>
        </div>
    """
      )

  fun renderResult(
      data: ResourceDetailsResult,
      sendCommand: (String) -> String,
      resources: List<FileSummaryResource>? = null,
  ): String {
    if (data.scanning) return renderPage("""<div class="state">Scanning...</div>""")
    if (data.needsLogin == true) return renderLogin(sendCommand)
    val resource =
        data.resource ?: return renderEmpty(sendCommand, loggedIn = true, resources = resources)
    return renderPage(renderResource(resource, sendCommand))
  }

  fun renderEmpty(
      sendCommand: (String) -> String,
      loggedIn: Boolean? = null,
      resources: List<FileSummaryResource>? = null,
  ): String {
    val statusText =
        when (loggedIn) {
          true -> """<span style="color:var(--green);">● Logged in</span>"""
          false -> """<span style="color:var(--fg-dim);">○ Not logged in</span>"""
          null -> ""
        }

    val resourcesHtml =
        if (!resources.isNullOrEmpty()) {
          val items =
              resources.joinToString("") { r ->
                val badges = buildString {
                  append("""<span class="badge">${esc(r.monthlyCost)}/mo</span>""")
                  if (r.policyIssues > 0) {
                    append("""<span class="badge high">${r.policyIssues} policy</span>""")
                  }
                  if (r.tagIssues > 0) {
                    append("""<span class="badge medium">${r.tagIssues} tag</span>""")
                  }
                }
                """<li><a href="#" onclick="${cmd(sendCommand, "revealResource:${r.line}")}; return false;"><div class="resource-link-name">${esc(r.name)}</div><div class="badges">$badges</div></a></li>"""
              }
          """<div class="section"><strong>Resources</strong><ul class="issue-list">$items</ul></div>"""
        } else {
          """<div class="state">No resource selected</div>"""
        }

    return renderPage(
        """$resourcesHtml
<div class="empty-links">
  <a href="#" onclick="${cmd(sendCommand, "troubleshoot")}; return false;">Troubleshooting</a>
  <a href="https://infracost.io/community-chat">Join the Slack</a>
  <a href="https://github.com/infracost/infracost/discussions">Raise an issue</a>
  ${if (statusText.isNotEmpty()) """<div class="login-status">$statusText</div>""" else ""}
</div>"""
    )
  }

  fun renderTroubleshooting(sendCommand: (String) -> String, status: StatusInfo): String {
    val serverStatus =
        if (!status.version.isNullOrEmpty()) {
          """<span style="color:var(--green);">● Server running</span>"""
        } else {
          """<span style="color:var(--error-fg);">● Server not running</span>"""
        }

    val loginStatus =
        if (status.loggedIn == true) {
          """<span style="color:var(--green);">● Logged in</span>"""
        } else {
          """<span style="color:var(--fg-dim);">○ Not logged in</span> — <a href="#" onclick="${cmd(sendCommand, "login")}; return false;">Login</a>"""
        }

    val projectCount = status.projectCount ?: 0
    val configStatus =
        if (status.configFound == true) {
          "Found ($projectCount project${if (projectCount != 1) "s" else ""})"
        } else {
          """<span style="color:var(--warning);">Not found</span>"""
        }

    val projects =
        if (!status.projectNames.isNullOrEmpty()) {
          status.projectNames.joinToString("") { """<li>${esc(it)}</li>""" }
        } else {
          """<li style="color:var(--fg-dim);">None</li>"""
        }

    return renderPage(
        """
<div class="back-nav"><a href="#" onclick="${cmd(sendCommand, "back")}; return false;">&larr; Back</a></div>
<div class="section status-indicators">
  <div>$serverStatus</div>
  <div>$loginStatus</div>
</div>
<div class="section">
  <strong>Details</strong>
  <table class="status-table">
    <tr><td>Server version</td><td>${esc(status.version ?: "N/A")}</td></tr>
    <tr><td>Workspace</td><td>${esc(status.workspaceRoot ?: "Not set")}</td></tr>
    <tr><td>Scan</td><td>${esc(if (status.scanning == true) "In progress" else "Idle")}</td></tr>
    <tr><td>Config</td><td>$configStatus</td></tr>
    <tr><td>Resources</td><td>${status.resourceCount ?: 0}</td></tr>
    <tr><td>Policy issues</td><td>${status.violationCount ?: 0}</td></tr>
    <tr><td>Tag issues</td><td>${status.tagIssueCount ?: 0}</td></tr>
  </table>
</div>
<div class="section">
  <strong>Projects</strong>
  <ul class="project-list">$projects</ul>
</div>
<div class="section">
  <strong>Actions</strong>
  <ul class="project-list">
    <li><a href="#" onclick="${cmd(sendCommand, "restartLsp")}; return false;">Restart language server</a></li>
    <li><a href="#" onclick="${cmd(sendCommand, "viewLogs")}; return false;">View logs</a></li>
    <li><a href="#" onclick="${cmd(sendCommand, "generateBundle")}; return false;">Generate support bundle</a></li>
  </ul>
</div>
"""
    )
  }

  private fun renderResource(r: ResourceDetail, sendCommand: (String) -> String): String {
    val parts = mutableListOf<String>()

    parts.add(
        """<div class="back-nav"><a href="#" onclick="${cmd(sendCommand, "back")}; return false;">&larr; Back</a></div>"""
    )

    parts.add(
        """
            <div class="header">
              <div class="resource-name">${esc(r.name)}</div>
              <div class="resource-cost">${esc(r.monthlyCost)}/mo</div>
            </div>
        """
    )

    if (!r.costComponents.isNullOrEmpty()) {
      val rows =
          r.costComponents.joinToString("") { c ->
            """<tr>
                    <td>${esc(c.name)}</td>
                    <td class="num">${esc(c.monthlyQuantity)}</td>
                    <td>${esc(c.unit)}</td>
                    <td class="num">${esc(c.price)}</td>
                    <td class="num">${esc(c.monthlyCost)}</td>
                </tr>"""
          }
      parts.add(
          """
                <details class="section" open>
                  <summary>Cost Components</summary>
                  <table>
                    <thead><tr><th>Component</th><th>Qty</th><th>Unit</th><th>Price</th><th>Monthly</th></tr></thead>
                    <tbody>$rows</tbody>
                  </table>
                </details>
            """
      )
    }

    if (!r.violations.isNullOrEmpty()) {
      parts.add(
          """
                <details class="section" open>
                  <summary>FinOps Issues (${r.violations.size})</summary>
                  ${r.violations.joinToString("") { renderViolation(it) }}
                </details>
            """
      )
    }

    if (!r.tagViolations.isNullOrEmpty()) {
      parts.add(
          """
                <details class="section" open>
                  <summary>Tag Issues (${r.tagViolations.size})</summary>
                  ${r.tagViolations.joinToString("") { renderTagViolation(it) }}
                </details>
            """
      )
    }

    return parts.joinToString("")
  }

  private fun renderViolation(v: ViolationDetail): String {
    val badges = mutableListOf<String>()
    if (v.blockPullRequest) {
      badges.add("""<span class="badge blocking">Blocking</span>""")
    }
    v.policyDetail?.risk?.let {
      badges.add("""<span class="badge ${cssClass(it)}">Risk: ${esc(sentenceCase(it))}</span>""")
    }
    v.policyDetail?.effort?.let {
      badges.add("""<span class="badge ${cssClass(it)}">Effort: ${esc(sentenceCase(it))}</span>""")
    }
    v.policyDetail?.downtime?.let {
      badges.add(
          """<span class="badge ${cssClass(it)}">Downtime: ${esc(sentenceCase(it))}</span>"""
      )
    }

    var details = ""
    v.policyDetail?.let { pd ->
      val rows = mutableListOf<String>()
      pd.riskDescription?.let {
        rows.add("""<div class="detail-row"><strong>Risk</strong><div>${linkify(it)}</div></div>""")
      }
      pd.effortDescription?.let {
        rows.add(
            """<div class="detail-row"><strong>Effort</strong><div>${linkify(it)}</div></div>"""
        )
      }
      pd.downtimeDescription?.let {
        rows.add(
            """<div class="detail-row"><strong>Downtime</strong><div>${linkify(it)}</div></div>"""
        )
      }
      pd.additionalDetails?.let { rows.add("""<div class="detail-row">${linkify(it)}</div>""") }
      if (rows.isNotEmpty()) {
        details = """<div class="policy-details">${rows.joinToString("")}</div>"""
      }
    }

    val savings =
        if (!v.monthlySavings.isNullOrBlank() && v.monthlySavings != "\$0.00") {
          """<div class="savings">Potential savings: ${esc(v.monthlySavings.trimStart('-'))}/mo</div>"""
        } else ""

    val title = v.policyDetail?.shortTitle ?: v.policyName

    return """
            <details class="violation">
              <summary>
                <strong>${esc(title)}</strong>
                <div class="badges">${badges.joinToString("")}</div>
              </summary>
              <div class="violation-message">${linkify(v.message)}</div>
              $savings
              $details
            </details>
        """
  }

  private fun renderTagViolation(v: TagViolationDetail): String {
    val badges = mutableListOf<String>()
    if (v.blockPR) {
      badges.add("""<span class="badge blocking">Blocking</span>""")
    }

    val tagList = buildString {
      if (!v.missingTags.isNullOrEmpty()) {
        append(
            """<div class="tag-list"><strong>Missing:</strong> ${v.missingTags.joinToString(", ") { "<code>${esc(it)}</code>" }}</div>"""
        )
      }
      v.invalidTags?.forEach { t ->
        append(
            """<div class="tag-list"><strong>${esc(t.key)}:</strong> <code>${esc(t.value)}</code>"""
        )
        t.suggestion?.let { append(""" (suggestion: <code>${esc(it)}</code>)""") }
        if (!t.validValues.isNullOrEmpty()) {
          append(
              """<div class="tag-list">Valid values: ${t.validValues.joinToString(", ") { "<code>${esc(it)}</code>" }}</div>"""
          )
        }
        append("</div>")
      }
    }

    val policyMsg =
        if (!v.policyMessage.isNullOrBlank()) {
          """<div class="policy-message">${esc(v.policyMessage)}</div>"""
        } else ""

    return """
            <div class="violation">
              <div class="violation-header">
                <strong>${esc(v.policyName)}</strong>
                <div class="badges">${badges.joinToString("")}</div>
              </div>
              $policyMsg
              <div class="violation-message">${linkify(v.message)}</div>
              $tagList
            </div>
        """
  }

  fun renderPage(body: String): String {
    val t = ThemeColors.fromIde()
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width,initial-scale=1.0">
        <style>
          :root {
            --bg: ${t.bg};
            --fg: ${t.fg};
            --fg-dim: ${t.fgDim};
            --border: ${t.border};
            --green: ${t.green};
            --error-bg: ${t.errorBg};
            --error-fg: ${t.errorFg};
            --warning: ${t.warning};
            --link: ${t.link};
            --code-bg: ${t.codeBg};
            --card-bg: ${t.cardBg};
            --btn-bg: ${t.btnBg};
            --btn-fg: ${t.btnFg};
            --btn-hover: ${t.btnHover};
          }
          body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            font-size: 13px;
            color: var(--fg);
            background: var(--bg);
            padding: 8px;
            margin: 0;
            position: relative;
            min-height: 100vh;
            box-sizing: border-box;
          }
          .state {
            text-align: center;
            padding: 24px 8px;
            color: var(--fg-dim);
          }
          .header {
            display: flex;
            justify-content: space-between;
            align-items: baseline;
            margin-bottom: 12px;
            padding-bottom: 8px;
            border-bottom: 1px solid var(--border);
          }
          .resource-name {
            font-weight: bold;
            word-break: break-all;
          }
          .resource-cost {
            font-weight: bold;
            white-space: nowrap;
            margin-left: 8px;
            color: var(--green);
          }
          .section { margin-bottom: 12px; }
          .section summary {
            cursor: pointer;
            font-weight: bold;
            padding: 4px 0;
            user-select: none;
          }
          table {
            width: 100%;
            border-collapse: collapse;
            font-size: 0.9em;
            margin-top: 4px;
          }
          th, td {
            text-align: left;
            padding: 3px 6px;
            border-bottom: 1px solid var(--border);
          }
          .num { text-align: right; }
          .violation {
            padding: 8px;
            margin: 6px 0;
            border-radius: 4px;
            background: var(--card-bg);
          }
          .violation > summary {
            cursor: pointer;
            list-style: revert;
            user-select: none;
          }
          .violation > summary .badges {
            display: inline-flex;
            gap: 4px;
            flex-wrap: wrap;
            vertical-align: middle;
            margin-left: 4px;
          }
          .violation-message {
            margin-top: 4px;
            color: var(--fg);
            line-height: 1.5;
          }
          .badges { display: flex; gap: 4px; flex-wrap: wrap; }
          .badge {
            display: inline-block;
            padding: 1px 6px;
            border-radius: 3px;
            font-size: 0.8em;
            font-weight: 600;
            background: var(--card-bg);
          }
          .badge.blocking {
            background: var(--error-bg);
            color: var(--error-fg);
          }
          .badge.high { color: var(--error-fg); }
          .badge.medium, .badge.yes { color: var(--warning); }
          .badge.low, .badge.no { color: var(--green); }
          a { color: var(--link); text-decoration: none; }
          a:hover { text-decoration: underline; }
          .savings {
            margin-top: 4px;
            color: var(--green);
            font-weight: 600;
          }
          .policy-details {
            margin-top: 6px;
            padding: 6px;
            border-radius: 3px;
            background: var(--bg);
            font-size: 0.9em;
            color: var(--fg);
          }
          .detail-row { margin: 6px 0; line-height: 1.5; }
          .tag-list {
            margin-top: 4px;
            font-size: 0.9em;
            line-height: 1.5;
          }
          code {
            background: var(--code-bg);
            padding: 1px 4px;
            border-radius: 3px;
            font-family: "JetBrains Mono", Menlo, Monaco, Consolas, monospace;
          }
          .login-btn {
            display: inline-block;
            padding: 6px 14px;
            margin-top: 12px;
            border: none;
            border-radius: 3px;
            cursor: pointer;
            font-size: 13px;
            font-family: inherit;
            background: var(--btn-bg);
            color: var(--btn-fg);
          }
          .login-btn:hover { background: var(--btn-hover); }
          .empty-links {
            position: absolute;
            bottom: 16px;
            left: 8px;
            right: 8px;
            display: flex;
            flex-direction: column;
            gap: 6px;
            font-size: 0.9em;
          }
          .empty-links a { color: var(--link); text-decoration: none; }
          .empty-links a:hover { text-decoration: underline; }
          .login-status { margin-top: 4px; font-size: 0.9em; }
          .issue-list { list-style: none; padding: 0; margin: 6px 0 0 0; }
          .issue-list li { padding: 4px 0; border-bottom: 1px solid var(--border); }
          .issue-list li:last-child { border-bottom: none; }
          .issue-list a { color: var(--fg); text-decoration: none; display: block; font-size: 0.9em; line-height: 1.4; }
          .issue-list a:hover { color: var(--link); }
          .resource-link-name { word-break: break-all; }
          .back-nav { margin-bottom: 8px; }
          .back-nav a { color: var(--link); text-decoration: none; font-size: 0.9em; }
          .back-nav a:hover { text-decoration: underline; }
          .status-table { font-size: 0.9em; }
          .status-table td:first-child { color: var(--fg-dim); white-space: nowrap; padding-right: 12px; }
          .status-table td { border-bottom: none; }
          .project-list { list-style: none; padding: 0; margin: 4px 0 0 0; font-size: 0.9em; }
          .project-list li { padding: 2px 0; }
          .status-indicators { display: flex; flex-direction: column; gap: 4px; font-size: 0.9em; }
          .policy-message { margin-top: 4px; font-size: 0.9em; }
        </style>
        </head>
        <body>$body</body>
        </html>
    """
        .trimIndent()
  }

  private fun esc(s: String): String =
      s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  private fun cssClass(s: String): String = s.lowercase().replace(Regex("[^a-z0-9-]"), "")

  private fun sentenceCase(s: String): String = s.first().uppercase() + s.drop(1).lowercase()

  private fun escAttr(s: String): String =
      s.replace("&", "&amp;")
          .replace("\"", "&quot;")
          .replace("'", "&#x27;")
          .replace("<", "&lt;")
          .replace(">", "&gt;")

  private fun linkify(s: String): String {
    // First, convert backticks to placeholders before escaping so they don't
    // interfere with the link regex output.
    val escaped = esc(s)

    // Restore <a> tags from the escaped string, with proper attribute escaping
    val withLinks =
        escaped.replace(Regex("""&lt;a href=&quot;(.*?)&quot;(.*?)&gt;(.*?)&lt;/a&gt;""")) { m ->
          val rawUrl = m.groupValues[1].replace("&amp;", "&")
          val text = m.groupValues[3] // already HTML-escaped by esc()
          if (rawUrl.startsWith("https://") || rawUrl.startsWith("http://")) {
            """<a href="${escAttr(rawUrl)}">$text</a>"""
          } else {
            text
          }
        }

    // Convert backtick-wrapped text to <code> tags, but only outside existing HTML tags
    return withLinks.replace(Regex("""`([^`]+)`""")) { m ->
      val content = m.groupValues[1]
      if (content.contains("<a ") || content.contains("</a>")) {
        m.value // leave it alone if it wraps a link
      } else {
        "<code>$content</code>"
      }
    }
  }
}
