package kernel.mail

data class MailAddress(
    val email: String,
    val name: String? = null
) {
    fun toHeaderValue(): String {
        return if (name.isNullOrBlank()) {
            email
        } else {
            "\"${name.replace("\"", "\\\"")}\" <$email>"
        }
    }
}
