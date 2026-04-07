package com.infracost.intellij

import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer

data class ResourceDetailsParams(
    val uri: String,
    val line: Int,
)

data class FileSummaryParams(
    val uri: String,
)

interface InfracostLanguageServer : LanguageServer {
  @JsonRequest("infracost/resourceDetails")
  fun resourceDetails(params: ResourceDetailsParams): CompletableFuture<Any>

  @JsonRequest("infracost/fileSummary")
  fun fileSummary(params: FileSummaryParams): CompletableFuture<Any>

  @JsonRequest("infracost/status") fun status(): CompletableFuture<Any>

  @JsonRequest("infracost/login") fun login(): CompletableFuture<Any>

  @JsonRequest("infracost/update") fun update(): CompletableFuture<Any>
}
