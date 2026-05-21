package kernel.http.server

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kernel.foundation.Application
import kotlin.io.path.exists
import kotlin.io.path.readText

object HttpServerRuntime {
    private const val STATE_RELATIVE_PATH = "storage/runtime/http-server.state"

    fun stateFile(application: Application): Path {
        val directory = application.path("storage/runtime")
        Files.createDirectories(directory)
        return application.path(STATE_RELATIVE_PATH)
    }

    fun writeState(
        application: Application,
        processId: Long,
        host: String,
        port: Int,
        urls: List<String>
    ) {
        val target = stateFile(application)
        val content = buildString {
            appendLine("pid=$processId")
            appendLine("host=$host")
            appendLine("port=$port")
            appendLine("urls=${urls.joinToString("|")}")
        }

        Files.writeString(target, content, StandardCharsets.UTF_8)
    }

    fun readState(application: Application): HttpServerState? {
        val source = stateFile(application)
        if (!source.exists()) {
            return null
        }

        val values = source.readText(StandardCharsets.UTF_8)
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                val parts = line.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()

        val pid = values["pid"]?.toLongOrNull() ?: return null
        val host = values["host"].orEmpty()
        val port = values["port"]?.toIntOrNull() ?: return null
        val urls = values["urls"]
            ?.split('|')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()

        return HttpServerState(
            pid = pid,
            host = host,
            port = port,
            urls = urls
        )
    }

    fun clearState(application: Application) {
        val target = stateFile(application)
        if (target.exists()) {
            Files.delete(target)
        }
    }
}

data class HttpServerState(
    val pid: Long,
    val host: String,
    val port: Int,
    val urls: List<String>
)
