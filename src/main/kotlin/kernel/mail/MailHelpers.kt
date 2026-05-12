package kernel.mail

import kernel.foundation.Application
import kernel.foundation.app

fun Application.mailManager(): MailManager {
    return config.get("services.mail.manager") as? MailManager
        ?: error("MailManager no esta registrado en services.mail.manager.")
}

fun mailManager(): MailManager = app().mailManager()
