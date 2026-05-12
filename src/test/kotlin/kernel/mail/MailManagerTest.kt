package kernel.mail

import kernel.foundation.Application
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MailManagerTest {
    @Test
    fun `mail manager resolves env-backed config and applies runtime overrides`() {
        val app = Application(Paths.get(".")).apply {
            loadConfig(
                namespace = "mail",
                values = mapOf(
                    "default" to "smtp",
                    "from" to mapOf(
                        "address" to "noreply@example.com",
                        "name" to "Kernel"
                    ),
                    "drivers" to mapOf(
                        "smtp" to mapOf(
                            "host" to "smtp.example.com",
                            "port" to 587,
                            "username" to "base-user",
                            "password" to "base-pass",
                            "auth" to true,
                            "timeoutMillis" to 1500
                        )
                    )
                )
            )
        }
        val manager = MailManager(app)

        val resolved = manager.resolveConfig(
            MailConfigOverrides(
                host = "dynamic.example.com",
                port = 2525,
                username = "dynamic-user"
            )
        )

        assertEquals("dynamic.example.com", resolved.host)
        assertEquals(2525, resolved.port)
        assertEquals("dynamic-user", resolved.username)
        assertEquals("base-pass", resolved.password)
        assertEquals("noreply@example.com", resolved.fromAddress)
    }

    @Test
    fun `mail manager delegates to the blocking task runner for async send`() {
        val app = Application(Paths.get(".")).apply {
            loadConfig(
                namespace = "mail",
                values = mapOf(
                    "default" to "smtp",
                    "from" to mapOf("address" to "noreply@example.com"),
                    "drivers" to mapOf(
                        "smtp" to mapOf(
                            "host" to "smtp.example.com",
                            "port" to 25,
                            "auth" to false
                        )
                    )
                )
            )
            config.set("services.tasks.blocking", RecordingBlockingTaskRunner())
        }
        val transport = RecordingMailTransport()
        val manager = MailManager(app, mapOf("smtp" to transport))

        val future = manager.send(SampleMail())
        val receipt = future.get()

        assertTrue((app.config.get("services.tasks.blocking") as RecordingBlockingTaskRunner).submitted)
        assertEquals(listOf("ada@example.com"), receipt.acceptedRecipients)
        assertEquals(1, transport.messages.size)
    }
}

private class RecordingMailTransport : MailTransport {
    val messages = mutableListOf<MailMessage>()

    override fun send(message: MailMessage, config: MailConfig): MailReceipt {
        messages += message
        return MailReceipt(
            messageId = "test-id",
            acceptedRecipients = message.to.map { it.email },
            driver = config.driver,
            queued = false
        )
    }
}

private class RecordingBlockingTaskRunner : kernel.concurrency.BlockingTaskRunner {
    var submitted: Boolean = false

    override fun <T> submit(task: () -> T): java.util.concurrent.Future<T> {
        submitted = true
        return CompletableFuture.completedFuture(task())
    }
}

private class SampleMail : Mailable() {
    override fun subject(): String = "Hola"

    override fun to(): List<MailAddress> = listOf(MailAddress("ada@example.com"))

    override fun body(): String = "Correo de prueba"
}
