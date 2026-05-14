package kernel.config

import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

data class ConfigCacheLoadResult(
    val loaded: Boolean,
    val path: Path,
    val environment: String?,
    val values: Map<String, Any?> = emptyMap(),
    val reason: String? = null
)

object ConfigBinaryCache {
    private const val RELATIVE_PATH = "bootstrap/cache/config.bin"

    fun path(basePath: Path): Path {
        return basePath.resolve(RELATIVE_PATH).normalize()
    }

    fun load(
        basePath: Path,
        expectedEnvironment: String
    ): ConfigCacheLoadResult {
        val target = path(basePath)
        if (!target.exists()) {
            return ConfigCacheLoadResult(
                loaded = false,
                path = target,
                environment = null,
                reason = "missing"
            )
        }

        val payload = runCatching {
            target.inputStream().use { input ->
                ObjectInputStream(input).use { stream ->
                    stream.readObject() as ConfigCachePayload
                }
            }
        }.getOrElse {
            return ConfigCacheLoadResult(
                loaded = false,
                path = target,
                environment = null,
                reason = "invalid"
            )
        }

        if (!payload.environment.equals(expectedEnvironment, ignoreCase = true)) {
            return ConfigCacheLoadResult(
                loaded = false,
                path = target,
                environment = payload.environment,
                reason = "environment-mismatch"
            )
        }

        return ConfigCacheLoadResult(
            loaded = true,
            path = target,
            environment = payload.environment,
            values = payload.values
        )
    }

    fun write(
        basePath: Path,
        environment: String,
        values: Map<String, Any?>
    ): Path {
        val target = path(basePath)
        Files.createDirectories(target.parent)

        val payload = ConfigCachePayload(
            environment = environment,
            values = normalizeMap(values)
        )

        target.outputStream().use { output ->
            ObjectOutputStream(output).use { stream ->
                stream.writeObject(payload)
                stream.flush()
            }
        }

        return target
    }

    fun clear(basePath: Path): Boolean {
        return Files.deleteIfExists(path(basePath))
    }

    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Map<*, *> -> normalizeMap(value)
            is Iterable<*> -> value.map(::normalizeValue)
            is Array<*> -> value.map(::normalizeValue)
            is Enum<*> -> value.name
            is Serializable -> value
            else -> value.toString()
        }
    }

    private fun normalizeMap(source: Map<*, *>): Map<String, Any?> {
        return linkedMapOf<String, Any?>().apply {
            source.forEach { (key, value) ->
                if (key != null) {
                    put(key.toString(), normalizeValue(value))
                }
            }
        }
    }

    private data class ConfigCachePayload(
        val environment: String,
        val values: Map<String, Any?>
    ) : Serializable
}
