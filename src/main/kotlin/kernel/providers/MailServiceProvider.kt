package kernel.providers

import kernel.mail.MailManager

open class MailServiceProvider(app: kernel.foundation.Application) : ServiceProvider(app) {
    override fun register() {
        val existing = app.config.get("services.mail.manager") as? MailManager
        if (existing != null) {
            return
        }

        app.config.set("services.mail.manager", MailManager(app))
    }
}
