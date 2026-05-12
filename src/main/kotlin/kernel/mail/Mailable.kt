package kernel.mail

abstract class Mailable {
    open fun subject(): String = ""

    open fun from(): MailAddress? = null

    open fun to(): List<MailAddress> = emptyList()

    open fun replyTo(): MailAddress? = null

    open fun headers(): Map<String, String> = emptyMap()

    open fun contentType(): String = "text/plain; charset=UTF-8"

    abstract fun body(): String

    internal fun toMessage(defaultFrom: MailAddress): MailMessage {
        val recipients = to()
        require(recipients.isNotEmpty()) {
            "El mailable `${this::class.simpleName}` debe definir al menos un destinatario."
        }

        return MailMessage(
            subject = subject(),
            body = body(),
            to = recipients,
            from = from() ?: defaultFrom,
            replyTo = replyTo(),
            headers = headers(),
            contentType = contentType()
        )
    }
}
