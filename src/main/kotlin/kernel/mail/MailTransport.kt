package kernel.mail

interface MailTransport {
    fun send(message: MailMessage, config: MailConfig): MailReceipt
}
