package com.infracost.intellij

import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

data class ResourceDetailsParams(
    val uri: String,
    val line: Int,
)

interface InfracostLanguageServer : LanguageServer {
    @JsonRequest("infracost/resourceDetails")
    fun resourceDetails(params: ResourceDetailsParams): CompletableFuture<Any>

    @JsonRequest("infracost/login")
    fun login(): CompletableFuture<Any>
}
