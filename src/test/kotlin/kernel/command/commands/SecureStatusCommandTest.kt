package kernel.command.commands

import kernel.command.CommandInput
import kernel.security.SecureRuntimeStatus
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecureStatusCommandTest {
    @Test
    fun `renders secure runtime diagnostic without exposing secrets`() {
        val command = SecureStatusCommand {
            SecureRuntimeStatus(
                osName = "Mac OS X",
                resourcePath = "/native/libksrjcbl.dylib",
                resourcePresent = true,
                nativeLoaded = true,
                fragmentSize = 32,
                loadError = null
            )
        }

        val result = command.execute(
            CommandInput(
                name = "secure:status",
                arguments = emptyList(),
                options = emptyMap(),
                workingDirectory = createTempDirectory("kernel-secure-status-test")
            )
        )

        val plain = result.message.withoutAnsi()
        assertEquals(0, result.exitCode)
        assertTrue(plain.contains("[INFO] Kernel Secure Runtime"))
        assertTrue(plain.contains("OS: Mac OS X"))
        assertTrue(plain.contains("Resource: /native/libksrjcbl.dylib"))
        assertTrue(plain.contains("Resource present: yes"))
        assertTrue(plain.contains("Native loaded: yes"))
        assertTrue(plain.contains("Fragment B size: 32 bytes"))
    }

    @Test
    fun `includes load error when native runtime is unavailable`() {
        val command = SecureStatusCommand {
            SecureRuntimeStatus(
                osName = "Linux",
                resourcePath = "/native/libksrjcbl.so",
                resourcePresent = false,
                nativeLoaded = false,
                fragmentSize = 32,
                loadError = "No se encontro la libreria nativa."
            )
        }

        val result = command.execute(
            CommandInput(
                name = "secure:status",
                arguments = emptyList(),
                options = emptyMap(),
                workingDirectory = createTempDirectory("kernel-secure-status-error-test")
            )
        )

        val plain = result.message.withoutAnsi()
        assertTrue(plain.contains("Load error: No se encontro la libreria nativa."))
    }

    private fun String.withoutAnsi(): String {
        return replace(Regex("\\u001B\\[[;\\d]*m"), "")
    }
}
