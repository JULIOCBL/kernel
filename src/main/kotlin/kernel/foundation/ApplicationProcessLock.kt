package kernel.foundation

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.readText

enum class ProcessLockMode {
    ENFORCE,
    OBSERVE,
    DISABLED
}

object ApplicationProcessLock {
    private val shutdownHooks = ConcurrentHashMap<Path, Thread>()

    fun pidFile(basePath: Path): Path {
        return basePath.resolve(".pid").normalize()
    }

    fun acquire(basePath: Path, mode: ProcessLockMode): Long {
        val pid = ProcessHandle.current().pid()
        if (mode == ProcessLockMode.DISABLED) {
            return pid
        }

        val pidFile = pidFile(basePath)
        val existingPid = readPid(basePath)

        if (mode == ProcessLockMode.ENFORCE && existingPid != null && existingPid != pid && isAlive(existingPid)) {
            error(
                "Ya existe una instancia activa para `${basePath.fileName}` " +
                    "con PID $existingPid. Cierra esa instancia antes de iniciar otra."
            )
        }

        if (mode == ProcessLockMode.ENFORCE) {
            writePid(basePath, pid)
            installShutdownHook(basePath, pid)
        }

        return pid
    }

    fun readPid(basePath: Path): Long? {
        val pidFile = pidFile(basePath)
        if (!pidFile.exists()) {
            return null
        }

        return pidFile.readText(StandardCharsets.UTF_8)
            .trim()
            .takeIf(String::isNotBlank)
            ?.toLongOrNull()
    }

    fun writePid(basePath: Path, pid: Long) {
        val pidFile = pidFile(basePath)
        Files.createDirectories(pidFile.parent)
        Files.writeString(pidFile, pid.toString(), StandardCharsets.UTF_8)
    }

    fun clear(basePath: Path, pid: Long? = null) {
        val pidFile = pidFile(basePath)
        if (!pidFile.exists()) {
            return
        }

        val current = readPid(basePath)
        if (pid != null && current != pid) {
            return
        }

        Files.deleteIfExists(pidFile)
    }

    fun isAlive(pid: Long): Boolean {
        return ProcessHandle.of(pid).orElse(null)?.isAlive == true
    }

    internal fun resetForTests() {
        shutdownHooks.values.forEach(Thread::interrupt)
        shutdownHooks.clear()
    }

    private fun installShutdownHook(basePath: Path, pid: Long) {
        val normalizedBasePath = basePath.toAbsolutePath().normalize()
        shutdownHooks.computeIfAbsent(normalizedBasePath) {
            Thread(
                { runCatching { clear(normalizedBasePath, pid) } },
                "kernel-process-lock-${normalizedBasePath.fileName}"
            ).also { hook ->
                Runtime.getRuntime().addShutdownHook(hook)
            }
        }
    }
}
