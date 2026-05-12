package kernel.mail

data class MailConfig(
    val driver: String = "smtp",
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val encryption: String? = null,
    val auth: Boolean = true,
    val timeoutMillis: Int = 10_000,
    val fromAddress: String? = null,
    val fromName: String? = null,
    val heloDomain: String? = null
) {
    fun from(): MailAddress? {
        val address = fromAddress?.trim().orEmpty()
        if (address.isBlank()) {
            return null
        }

        return MailAddress(address, fromName?.trim()?.takeIf(String::isNotBlank))
    }
}

data class MailConfigOverrides(
    val driver: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val username: String? = null,
    val password: String? = null,
    val encryption: String? = null,
    val auth: Boolean? = null,
    val timeoutMillis: Int? = null,
    val fromAddress: String? = null,
    val fromName: String? = null,
    val heloDomain: String? = null
) {
    fun applyTo(base: MailConfig): MailConfig {
        return base.copy(
            driver = driver ?: base.driver,
            host = host ?: base.host,
            port = port ?: base.port,
            username = username ?: base.username,
            password = password ?: base.password,
            encryption = encryption ?: base.encryption,
            auth = auth ?: base.auth,
            timeoutMillis = timeoutMillis ?: base.timeoutMillis,
            fromAddress = fromAddress ?: base.fromAddress,
            fromName = fromName ?: base.fromName,
            heloDomain = heloDomain ?: base.heloDomain
        )
    }
}
