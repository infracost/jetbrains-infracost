package com.infracost.intellij

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer

data class ResourceDetailsParams(
    val uri: String,
    val line: Int,
)

data class SelectOrgParams(
    val orgId: String,
)

data class GuardrailStatus(
    val name: String = "",
    val message: String = "",
    val blockPr: Boolean = false,
    val totalMonthlyCost: String? = null,
    val threshold: String? = null,
)

data class OrgEntry(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
)

data class OrgInfo(
    val organizations: List<OrgEntry>? = null,
    val selectedOrgId: String? = null,
    val hasExplicitSelection: Boolean = false,
)

data class WorkspaceSummaryResource(
    val name: String = "",
    val line: Int = 0,
    val monthlyCost: String = "",
    val policyIssues: Int = 0,
    val tagIssues: Int = 0,
)

data class WorkspaceSummaryFile(
    val name: String = "",
    val uri: String = "",
    val resources: List<WorkspaceSummaryResource>? = null,
)

data class WorkspaceSummaryResult(
    val files: List<WorkspaceSummaryFile>? = null,
)

interface InfracostLanguageServer : LanguageServer {
  @JsonRequest("infracost/resourceDetails")
  fun resourceDetails(params: ResourceDetailsParams): CompletableFuture<Any>

  @JsonRequest("infracost/status") fun status(): CompletableFuture<Any>

  @JsonRequest("infracost/login") fun login(): CompletableFuture<Any>

  @JsonRequest("infracost/update") fun update(): CompletableFuture<Any>

  @JsonRequest("infracost/logout") fun logout(): CompletableFuture<Any>

  @JsonRequest("infracost/orgs") fun orgs(): CompletableFuture<Any>

  @JsonRequest("infracost/selectOrg")
  fun selectOrg(params: SelectOrgParams): CompletableFuture<Any>

  @JsonRequest("infracost/workspaceSummary") fun workspaceSummary(): CompletableFuture<Any>
}
