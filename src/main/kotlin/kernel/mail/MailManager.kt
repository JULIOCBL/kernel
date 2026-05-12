package kernel.mail

import kernel.concurrency.blockingTaskRunner
import kernel.foundation.Application
import java.util.concurrent.Future

class MailManager(
    private val app: Application,
    private val transports: Map<String, MailTransport> = mapOf(
        "smtp" to SmtpMailTransport()
    )
) {
    fun send(
        mailable: Mailable,
        overrides: MailConfigOverrides? = null
    ): Future<MailReceipt> {
        return app.blockingTaskRunner().submit {
            sendNow(mailable, overrides)
        }
    }

    fun sendNow(
        mailable: Mailable,
        overrides: MailConfigOverrides? = null
    ): MailReceipt {
        val config = resolveConfig(overrides)
        val from = config.from()
            ?: error("La configuracion de mail debe definir MAIL_FROM_ADDRESS o fromAddress dinamico.")
        val message = mailable.toMessage(from)
        val transport = transports[config.driver]
            ?: error("No existe un transporte de mail registrado para `${config.driver}`.")

        return transport.send(message, config)
    }

    fun resolveConfig(overrides: MailConfigOverrides? = null): MailConfig {
        val driver = overrides?.driver ?: app.config.string("mail.default", "smtp")
        val namespace = "mail.drivers.$driver"
        val host = app.config.string("$namespace.host")
        require(host.isNotBlank()) { "mail.drivers.$driver.host no puede estar vacio." }

        val base = MailConfig(
            driver = driver,
            host = host,
            port = app.config.int("$namespace.port", 1025),
            username = app.config.string("$namespace.username").ifBlank { null },
            password = app.config.string("$namespace.password").ifBlank { null },
            encryption = app.config.string("$namespace.encryption").ifBlank { null },
            auth = app.config.bool("$namespace.auth", true),
            timeoutMillis = app.config.int("$namespace.timeoutMillis", 10000),
            fromAddress = app.config.string("mail.from.address").ifBlank { null },
            fromName = app.config.string("mail.from.name").ifBlank { null },
            heloDomain = app.config.string("$namespace.heloDomain").ifBlank { null }
        )

        return overrides?.applyTo(base) ?: base
    }
}
