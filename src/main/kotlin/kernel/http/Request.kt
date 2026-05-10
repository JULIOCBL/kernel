package kernel.http

import kernel.foundation.Application
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.reflect.KClass

data class UploadedFile(
    val field: String,
    val originalName: String,
    val contentType: String?,
    val bytes: ByteArray,
    val temporaryPath: Path? = null,
    val storageRoot: Path? = null,
    val uploadError: String? = null
) {
    val size: Long
        get() = bytes.size.toLong()

    val extension: String
        get() = inferExtension()

    fun isValid(): Boolean = uploadError == null

    fun path(): String? = temporaryPath?.toString()

    fun store(directory: String, disk: String? = null): String {
        val targetRoot = resolveStorageRoot(disk)
        val normalizedDirectory = directory.trim().trim('/')
        val targetDirectory = if (normalizedDirectory.isBlank()) {
            targetRoot
        } else {
            targetRoot.resolve(normalizedDirectory)
        }
        Files.createDirectories(targetDirectory)

        val fileName = buildString {
            append(UUID.randomUUID())
            if (extension.isNotBlank()) {
                append('.')
                append(extension)
            }
        }

        Files.write(targetDirectory.resolve(fileName), bytes)

        return if (normalizedDirectory.isBlank()) {
            fileName
        } else {
            "$normalizedDirectory/$fileName"
        }
    }

    fun storeAs(directory: String, fileName: String, disk: String? = null): String {
        val targetRoot = resolveStorageRoot(disk)
        val normalizedDirectory = directory.trim().trim('/')
        val targetDirectory = if (normalizedDirectory.isBlank()) {
            targetRoot
        } else {
            targetRoot.resolve(normalizedDirectory)
        }
        Files.createDirectories(targetDirectory)
        Files.write(targetDirectory.resolve(fileName), bytes)

        return if (normalizedDirectory.isBlank()) {
            fileName
        } else {
            "$normalizedDirectory/$fileName"
        }
    }

    private fun resolveStorageRoot(disk: String?): Path {
        return storageRoot
            ?: error(
                "El archivo `$field` no conoce un storage root. " +
                    "Proporcionalo desde el runtime HTTP antes de llamar store${disk?.let { " en `$it`" }.orEmpty()}."
            )
    }

    private fun inferExtension(): String {
        val fromName = originalName.substringAfterLast('.', "").lowercase()
        if (fromName.isNotBlank()) {
            return fromName
        }

        return when (contentType?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            else -> ""
        }
    }
}

open class Request(
    val app: Application,
    val method: String,
    val target: String,
    val path: String,
    queryParams: Map<String, String> = emptyMap(),
    body: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    files: Map<String, UploadedFile> = emptyMap(),
    routeParams: Map<String, String> = emptyMap(),
    val rawBody: String = "",
    val remoteAddress: String? = null,
    attributes: Map<String, Any?> = emptyMap()
) {
    private val mutableQueryParams = linkedMapOf<String, String>().apply { putAll(queryParams) }
    private val mutableBody = linkedMapOf<String, String>().apply { putAll(body) }
    private val mutableHeaders = linkedMapOf<String, String>().apply {
        headers.forEach { (key, value) -> put(key.lowercase(), value) }
    }
    private val mutableFiles = linkedMapOf<String, UploadedFile>().apply { putAll(files) }
    private val mutableRouteParams = linkedMapOf<String, String>().apply { putAll(routeParams) }
    private val mutableAttributes = linkedMapOf<String, Any?>().apply { putAll(attributes) }

    open val queryParams: Map<String, String>
        get() = mutableQueryParams.toMap()

    open val body: Map<String, String>
        get() = mutableBody.toMap()

    open val headers: Map<String, String>
        get() = mutableHeaders.toMap()

    open val files: Map<String, UploadedFile>
        get() = mutableFiles.toMap()

    open val routeParams: Map<String, String>
        get() = mutableRouteParams.toMap()

    fun method(): String = method

    fun isMethod(candidate: String): Boolean {
        return method.equals(candidate.trim(), ignoreCase = true)
    }

    fun input(key: String, default: String? = null): String? {
        return resolveInputValue(mutableBody, key)
            ?: resolveInputValue(mutableQueryParams, key)
            ?: resolveInputValue(mutableRouteParams, key)
            ?: default
    }

    fun query(key: String? = null, default: String? = null): Any? {
        return if (key == null) {
            queryParams
        } else {
            resolveInputValue(mutableQueryParams, key) ?: default
        }
    }

    fun post(key: String? = null, default: String? = null): Any? {
        return if (key == null) {
            body
        } else {
            resolveInputValue(mutableBody, key) ?: default
        }
    }

    fun json(key: String? = null, default: String? = null): Any? {
        return post(key, default)
    }

    fun string(key: String, default: String? = null): String? = input(key, default)

    fun int(key: String, default: Int? = null): Int? = input(key)?.toIntOrNull() ?: default

    fun long(key: String, default: Long? = null): Long? = input(key)?.toLongOrNull() ?: default

    fun double(key: String, default: Double? = null): Double? = input(key)?.toDoubleOrNull() ?: default

    fun boolean(key: String, default: Boolean? = null): Boolean? {
        return when (input(key)?.trim()?.lowercase()) {
            null -> default
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> default
        }
    }

    fun array(key: String): List<String> {
        val value = input(key)?.trim().orEmpty()
        if (value.isBlank()) {
            return emptyList()
        }

        return value.split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    fun list(key: String): List<String> = array(key)

    fun map(key: String): Map<String, String> {
        val value = input(key)?.trim().orEmpty()
        if (value.isBlank()) {
            return emptyMap()
        }

        return if (value.startsWith("{") && value.endsWith("}")) {
            parseJsonLikeMap(value)
        } else {
            value.split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
                .associate { token ->
                    val index = token.indexOf(':')
                    require(index > 0) {
                        "El valor `$value` no puede convertirse a mapa."
                    }
                    token.substring(0, index).trim() to token.substring(index + 1).trim()
                }
        }
    }

    fun <E : Enum<E>> enum(
        key: String,
        enumType: KClass<E>,
        default: E? = null
    ): E? {
        val raw = input(key)?.trim()?.takeIf(String::isNotBlank) ?: return default
        return enumType.java.enumConstants.firstOrNull { constant ->
            constant.name.equals(raw, ignoreCase = true)
        } ?: default
    }

    fun header(key: String, default: String? = null): String? {
        return mutableHeaders[key.lowercase()] ?: default
    }

    fun headers(): Map<String, String> = headers

    fun keys(): List<String> = all().keys.toList()

    fun has(key: String): Boolean = input(key)?.isNotEmpty() == true

    fun hasAny(keys: Iterable<String>): Boolean = keys.any(::has)

    fun filled(key: String): Boolean = !input(key).isNullOrBlank()

    fun isNotFilled(key: String): Boolean = !filled(key)

    fun isNotFilled(keys: Iterable<String>): Boolean = keys.all(::isNotFilled)

    fun missing(key: String): Boolean = input(key) == null

    fun merge(values: Map<String, String>): Request {
        mutableBody.putAll(values)
        return this
    }

    fun mergeIfMissing(values: Map<String, String>): Request {
        values.forEach { (key, value) ->
            if (missing(key)) {
                mutableBody[key] = value
            }
        }
        return this
    }

    fun only(vararg keys: String): Map<String, String> {
        val requested = keys.toSet()
        return all().filterKeys { it in requested }
    }

    fun except(vararg keys: String): Map<String, String> {
        val ignored = keys.toSet()
        return all().filterKeys { it !in ignored }
    }

    fun collect(): Map<String, String> = all()

    fun collect(key: String): List<String> = array(key)

    fun whenHas(
        key: String,
        callback: (String) -> Unit,
        default: (() -> Unit)? = null
    ): Request {
        val value = input(key)
        if (value != null) {
            callback(value)
        } else {
            default?.invoke()
        }

        return this
    }

    fun whenFilled(
        key: String,
        callback: (String) -> Unit,
        default: (() -> Unit)? = null
    ): Request {
        val value = input(key)
        if (!value.isNullOrBlank()) {
            callback(value)
        } else {
            default?.invoke()
        }

        return this
    }

    fun whenMissing(
        key: String,
        callback: () -> Unit,
        default: ((String) -> Unit)? = null
    ): Request {
        val value = input(key)
        if (value == null) {
            callback()
        } else {
            default?.invoke(value)
        }

        return this
    }

    fun all(): Map<String, String> {
        return linkedMapOf<String, String>()
            .apply {
                putAll(mutableRouteParams)
                putAll(mutableQueryParams)
                putAll(mutableBody)
            }
    }

    fun file(key: String): UploadedFile? = mutableFiles[key]

    fun hasFile(key: String): Boolean = file(key)?.isValid() == true

    fun route(key: String? = null): Any? {
        return if (key == null) {
            mutableRouteParams.toMap()
        } else {
            resolveInputValue(mutableRouteParams, key)
        }
    }

    fun path(): String = path.trim().trim('/')

    fun segments(): List<String> {
        return path()
            .split('/')
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    fun segment(index: Int, default: String? = null): String? {
        require(index >= 1) { "Los segmentos inician en 1." }
        return segments().getOrNull(index - 1) ?: default
    }

    fun host(): String? {
        return header("host")
            ?: target.substringAfter("://", "").substringBefore('/').ifBlank { null }
    }

    fun url(): String {
        val host = host().orEmpty()
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return "http://$host$normalizedPath"
    }

    fun fullUrl(): String {
        val queryString = buildQueryString(mutableQueryParams)
        return if (queryString.isBlank()) {
            url()
        } else {
            "${url()}?$queryString"
        }
    }

    fun fullUrlWithQuery(values: Map<String, String>): String {
        val merged = linkedMapOf<String, String>()
        merged.putAll(mutableQueryParams)
        merged.putAll(values)
        val queryString = buildQueryString(merged)
        return if (queryString.isBlank()) {
            url()
        } else {
            "${url()}?$queryString"
        }
    }

    fun fullUrlWithoutQuery(vararg keys: String): String {
        val ignored = keys.toSet()
        val queryString = buildQueryString(
            mutableQueryParams.filterKeys { it !in ignored }
        )
        return if (queryString.isBlank()) {
            url()
        } else {
            "${url()}?$queryString"
        }
    }

    fun ip(): String? {
        return header("x-forwarded-for")
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: header("x-real-ip")
            ?: remoteAddress
    }

    fun expectsJson(): Boolean {
        return header("accept")?.contains("application/json", ignoreCase = true) == true ||
            header("x-requested-with")?.equals("XMLHttpRequest", ignoreCase = true) == true
    }

    fun accepts(contentTypes: String): Boolean = accepts(listOf(contentTypes))

    fun accepts(contentTypes: List<String>): Boolean {
        val accepted = acceptedContentTypes()
        if (accepted.isEmpty()) {
            return false
        }

        return contentTypes.any { candidate ->
            accepted.any { acceptedType ->
                mimeMatches(acceptedType, candidate)
            }
        }
    }

    fun prefers(contentTypes: List<String>): String? {
        val accepted = acceptedContentTypes()
        if (accepted.isEmpty()) {
            return null
        }

        accepted.forEach { acceptedType ->
            contentTypes.firstOrNull { candidate ->
                mimeMatches(acceptedType, candidate)
            }?.let { return it }
        }

        return null
    }

    fun wantsJson(): Boolean {
        val accepted = acceptedContentTypes()
        return accepted.firstOrNull()?.let { mimeMatches(it, "application/json") } == true
    }

    fun acceptsJson(): Boolean = accepts("application/json")

    fun bearerToken(): String? {
        val header = header("authorization") ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) {
            return null
        }

        return header.removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
            .ifBlank { null }
    }

    fun user(): Any? = attribute("user")

    fun setUser(user: Any?): Request = setAttribute("user", user)

    @Suppress("UNCHECKED_CAST")
    fun <T> attribute(key: String): T? = mutableAttributes[key] as? T

    fun setAttribute(key: String, value: Any?): Request {
        mutableAttributes[key] = value
        return this
    }

    fun attributes(): Map<String, Any?> = mutableAttributes.toMap()

    fun withRouteParams(params: Map<String, String>): Request {
        return Request(
            app = app,
            method = method,
            target = target,
            path = path,
            queryParams = mutableQueryParams,
            body = mutableBody,
            headers = mutableHeaders,
            files = mutableFiles,
            routeParams = params,
            rawBody = rawBody,
            remoteAddress = remoteAddress,
            attributes = mutableAttributes
        )
    }

    private fun resolveInputValue(source: Map<String, String>, key: String): String? {
        source[key]?.let { return it }
        if (!key.contains('.')) {
            return null
        }

        val uri = URI.create("http://kernel.local/?${source.entries.joinToString("&") { (entryKey, value) ->
            "${urlEncode(entryKey)}=${urlEncode(value)}"
        }}")
        val flat = mutableMapOf<String, String>()
        uri.rawQuery?.split('&')
            ?.filter(String::isNotBlank)
            ?.forEach { pair ->
                val index = pair.indexOf('=')
                if (index > 0) {
                    flat[URLDecoderCompat.decode(pair.substring(0, index))] =
                        URLDecoderCompat.decode(pair.substring(index + 1))
                }
            }

        return flat[key]
    }

    private fun buildQueryString(values: Map<String, String>): String {
        return values.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    private fun parseJsonLikeMap(value: String): Map<String, String> {
        return value
            .removePrefix("{")
            .removeSuffix("}")
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .associate { token ->
                val index = token.indexOf(':')
                require(index > 0) {
                    "El valor `$value` no puede convertirse a mapa."
                }
                token.substring(0, index).trim().trim('"') to
                    token.substring(index + 1).trim().trim('"')
            }
    }

    private fun acceptedContentTypes(): List<String> {
        return header("accept")
            ?.split(',')
            ?.map { token -> token.substringBefore(';').trim().lowercase() }
            ?.filter(String::isNotBlank)
            .orEmpty()
    }

    private fun mimeMatches(accepted: String, candidate: String): Boolean {
        val normalizedCandidate = candidate.trim().lowercase()
        val normalizedAccepted = accepted.trim().lowercase()

        return normalizedAccepted == "*/*" ||
            normalizedAccepted == normalizedCandidate ||
            (
                normalizedAccepted.endsWith("/*") &&
                    normalizedCandidate.startsWith(normalizedAccepted.substringBefore('/'))
                )
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private object URLDecoderCompat {
        fun decode(value: String): String = java.net.URLDecoder.decode(value, Charsets.UTF_8)
    }
}
