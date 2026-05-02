package kernel.security

import kernel.foundation.OS
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Resuelve un identificador de hardware/OS con la mayor persistencia posible.
 *
 * Si solo puede derivar una huella efimera del entorno, falla de forma
 * preventiva para evitar cifrar datos con una llave que podria cambiar al
 * reiniciar el host, contenedor o volumen temporal.
 */
object HardwareIdResolver {
    @Volatile
    private var supplier: (() -> Resolution)? = null

    fun currentId(): String {
        val resolution = (supplier ?: ::resolveCurrent).invoke()
        require(resolution.persistent) {
            "HardwareIdResolver detecto un identificador potencialmente efimero. " +
                "Configura DEV_FIXED_HARDWARE_ID en desarrollo o estabiliza el entorno antes de cifrar la base."
        }

        return resolution.value
    }

    internal fun installSupplierForTests(supplier: () -> Resolution) {
        this.supplier = supplier
    }

    internal fun resetSupplierForTests() {
        supplier = null
    }

    private fun resolveCurrent(): Resolution {
        return when {
            OS.isMac -> firstPersistent(
                commandOutput("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                    ?.lineSequence()
                    ?.firstOrNull { it.contains("IOPlatformUUID") }
                    ?.substringAfter("=")
                    ?.replace("\"", "")
                    ?.trim()
            )

            OS.isLinux -> firstPersistent(
                readFile("/etc/machine-id"),
                readFile("/var/lib/dbus/machine-id")
            )

            OS.isWindows -> firstPersistent(
                commandOutput("wmic", "csproduct", "get", "uuid")
                    ?.lineSequence()
                    ?.drop(1)
                    ?.firstOrNull()
                    ?.trim(),
                commandOutput(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "(Get-CimInstance Win32_ComputerSystemProduct).UUID"
                )?.trim()
            )

            else -> fallbackResolution()
        }
    }

    private fun firstPersistent(vararg candidates: String?): Resolution {
        val value = candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
        return if (value.isNullOrBlank()) {
            fallbackResolution()
        } else {
            Resolution(value = value, persistent = true)
        }
    }

    private fun readFile(path: String): String? {
        val target = Path.of(path)
        if (!Files.exists(target)) {
            return null
        }

        return runCatching {
            Files.readString(target).trim().takeIf(String::isNotEmpty)
        }.getOrNull()
    }

    private fun commandOutput(vararg command: String): String? {
        return runCatching {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val finished = process.waitFor()

            if (finished == 0) {
                output.takeIf(String::isNotEmpty)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun fallbackResolution(): Resolution {
        val seed = buildString {
            append(System.getProperty("os.name").orEmpty())
            append('|')
            append(System.getProperty("os.version").orEmpty())
            append('|')
            append(System.getProperty("user.name").orEmpty())
            append('|')
            append(System.getProperty("user.home").orEmpty())
            append('|')
            append(runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("unknown-host"))
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val fingerprint = digest.joinToString("") { "%02x".format(it) }

        return Resolution(value = fingerprint, persistent = false)
    }

    internal data class Resolution(
        val value: String,
        val persistent: Boolean
    )
}
