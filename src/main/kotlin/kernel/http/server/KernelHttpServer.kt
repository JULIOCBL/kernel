package kernel.http.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kernel.foundation.Application
import kernel.foundation.HttpKernel
import kernel.http.JsonResponse
import kernel.http.KernelResponse
import kernel.http.Request
import kernel.http.TextResponse
import kernel.http.UploadedFile
import kernel.http.ViewResponse
import kernel.routing.ApiRouter

class KernelHttpServer(
    private val application: Application,
    private val router: ApiRouter
) : AutoCloseable {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    fun start(
        host: String,
        port: Int,
        backlog: Int
    ) {
        check(server == null) {
            "El servidor HTTP ya esta iniciado."
        }

        val httpServer = HttpServer.create(InetSocketAddress(host, port), backlog)
        val executorService = buildExecutor()

        httpServer.executor = executorService
        httpServer.createContext("/") { exchange ->
            handle(exchange)
        }
        httpServer.start()

        server = httpServer
        executor = executorService
    }

    fun await() {
        CountDownLatch(1).await()
    }

    fun listeningPort(): Int? = server?.address?.port

    override fun close() {
        server?.stop(0)
        server = null
        executor?.shutdown()
        executor = null
    }

    private fun handle(exchange: HttpExchange) {
        val method = exchange.requestMethod.uppercase()
        val uri = exchange.requestURI
        val path = uri.path.trim().trim('/')
        val query = uri.rawQuery?.takeIf { it.isNotBlank() }?.let { "?$it" }.orEmpty()
        val scheme = application.config.string("app.routing.api.scheme", "api")
        val internalUri = if (path.isBlank()) {
            "$scheme://$query".removeSuffix("?")
        } else {
            "$scheme://$path$query"
        }
        val httpKernel = application.config.get("services.http.kernel") as? HttpKernel
            ?: HttpKernel(application)

        try {
            val bodyBytes = exchange.requestBody.use { input -> input.readBytes() }
            val rawBody = bodyBytes.toString(Charsets.UTF_8)
            val payload = extractPayload(exchange, bodyBytes, rawBody)
            val request = Request(
                app = application,
                method = method,
                target = internalUri,
                path = uri.path,
                queryParams = extractQueryParams(uri.rawQuery),
                body = payload.body,
                headers = exchange.requestHeaders.entries.associate { (key, value) ->
                    key.lowercase() to value.joinToString(", ")
                },
                files = payload.files,
                rawBody = rawBody,
                remoteAddress = exchange.remoteAddress?.address?.hostAddress
            )
            writeResponse(exchange, httpKernel.handle(request, router))
        } catch (error: Exception) {
            val fallbackRequest = Request(
                app = application,
                method = method,
                target = internalUri,
                path = uri.path,
                queryParams = extractQueryParams(uri.rawQuery),
                headers = exchange.requestHeaders.entries.associate { (key, value) ->
                    key.lowercase() to value.joinToString(", ")
                },
                remoteAddress = exchange.remoteAddress?.address?.hostAddress
            )
            writeResponse(exchange, httpKernel.renderException(fallbackRequest, error))
        }
    }

    private fun extractQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) {
            return emptyMap()
        }

        return rawQuery
            .split('&')
            .filter(String::isNotBlank)
            .associate { pair ->
                val parts = pair.split('=', limit = 2)
                val key = decode(parts.first())
                val value = decode(parts.getOrNull(1).orEmpty())
                key to value
            }
    }

    private fun extractPayload(
        exchange: HttpExchange,
        bodyBytes: ByteArray,
        rawBody: String
    ): ParsedPayload {
        val contentType = exchange.requestHeaders.getFirst("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

        if (bodyBytes.isEmpty()) {
            return ParsedPayload()
        }

        return when (contentType) {
            "application/json" -> ParsedPayload(body = JsonCodec.decodeObject(rawBody))
            "application/x-www-form-urlencoded" -> ParsedPayload(body = extractQueryParams(rawBody))
            "multipart/form-data" -> parseMultipart(exchange, bodyBytes)
            else -> ParsedPayload()
        }
    }

    private fun parseMultipart(
        exchange: HttpExchange,
        bodyBytes: ByteArray
    ): ParsedPayload {
        val contentTypeHeader = exchange.requestHeaders.getFirst("Content-Type").orEmpty()
        val boundary = contentTypeHeader
            .split(';')
            .map(String::trim)
            .firstOrNull { it.startsWith("boundary=") }
            ?.substringAfter("boundary=")
            ?.trim('"')
            .orEmpty()

        if (boundary.isBlank()) {
            return ParsedPayload()
        }

        val delimiter = "--$boundary"
        val content = bodyBytes.toString(Charsets.ISO_8859_1)
        val parts = content.split(delimiter)
            .drop(1)
            .dropLastWhile { it.trim().isEmpty() || it.trim() == "--" }

        val body = linkedMapOf<String, String>()
        val files = linkedMapOf<String, UploadedFile>()

        parts.forEach { part ->
            val normalized = part.removePrefix("\r\n").removeSuffix("\r\n")
            if (normalized == "--") {
                return@forEach
            }

            val headerSeparator = normalized.indexOf("\r\n\r\n")
            if (headerSeparator <= 0) {
                return@forEach
            }

            val rawHeaders = normalized.substring(0, headerSeparator)
            val rawContent = normalized.substring(headerSeparator + 4)
            val headers = rawHeaders
                .split("\r\n")
                .mapNotNull { line ->
                    val index = line.indexOf(':')
                    if (index <= 0) {
                        null
                    } else {
                        line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
                    }
                }
                .toMap()

            val disposition = headers["content-disposition"].orEmpty()
            val fieldName = disposition.substringAfter("name=\"", "").substringBefore('"')
            if (fieldName.isBlank()) {
                return@forEach
            }

            val filename = disposition.substringAfter("filename=\"", "").substringBefore('"')
            val contentBytes = rawContent.removeSuffix("\r\n").toByteArray(Charsets.ISO_8859_1)

            if (filename.isNotBlank()) {
                files[fieldName] = UploadedFile(
                    field = fieldName,
                    originalName = filename,
                    contentType = headers["content-type"],
                    bytes = contentBytes,
                    storageRoot = application.path("storage/app")
                )
            } else {
                body[fieldName] = rawContent.removeSuffix("\r\n")
            }
        }

        return ParsedPayload(body = body, files = files)
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8)
    }

    private fun writeJson(
        exchange: HttpExchange,
        status: Int,
        payload: Any?
    ) {
        val body = JsonCodec.encode(payload).toByteArray(Charsets.UTF_8)

        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { stream ->
            stream.write(body)
        }
    }

    private fun writeText(
        exchange: HttpExchange,
        status: Int,
        content: String,
        contentType: String
    ) {
        val body = content.toByteArray(Charsets.UTF_8)

        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { stream ->
            stream.write(body)
        }
    }

    private fun writeResponse(
        exchange: HttpExchange,
        response: KernelResponse
    ) {
        when (response) {
            is JsonResponse -> writeJson(exchange, response.status, response.payload)
            is ViewResponse -> writeJson(exchange, 200, response.view.model)
            is TextResponse -> writeText(
                exchange = exchange,
                status = response.status,
                content = response.content,
                contentType = response.contentType
            )
        }
    }

    private fun buildExecutor(): ExecutorService {
        return Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "kernel-http-server").apply {
                isDaemon = true
            }
        }
    }

    private data class ParsedPayload(
        val body: Map<String, String> = emptyMap(),
        val files: Map<String, UploadedFile> = emptyMap()
    )
}
