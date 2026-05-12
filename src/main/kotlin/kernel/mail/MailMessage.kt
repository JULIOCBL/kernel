package kernel.mail

import java.util.UUID

data class MailMessage(
    val subject: String,
    val body: String,
    val to: List<MailAddress>,
    val from: MailAddress,
    val replyTo: MailAddress? = null,
    val headers: Map<String, String> = emptyMap(),
    val contentType: String = "text/plain; charset=UTF-8"
)

data class MailReceipt(
    val messageId: String = UUID.randomUUID().toString(),
    val acceptedRecipients: List<String>,
    val driver: String,
    val queued: Boolean
)
