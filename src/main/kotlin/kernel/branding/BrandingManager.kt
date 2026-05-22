package kernel.branding

import kernel.foundation.Application
import kernel.foundation.OS
import kernel.foundation.OSType

class BrandingManager(
    private val app: Application
) {
    fun current(): AppBranding {
        return AppBranding(
            name = app.config.string("branding.name", app.config.string("app.name", "")),
            shortName = app.config.string("branding.shortName").ifBlank { null },
            logoText = app.config.string("branding.logoText").ifBlank { null },
            logo = BrandingLogoSet(
                wordmark = resourcePathOrNull("branding.logo.wordmark"),
                mark = resourcePathOrNull("branding.logo.mark"),
                serviceMark = resourcePathOrNull("branding.logo.serviceMark")
            ),
            icons = AppIconSet(
                window = resourcePathOrNull("branding.icons.window"),
                tray = resourcePathOrNull("branding.icons.tray"),
                service = resourcePathOrNull("branding.icons.service"),
                windows = resourcePathOrNull("branding.icons.windows"),
                macos = resourcePathOrNull("branding.icons.macos"),
                linux = resourcePathOrNull("branding.icons.linux")
            )
        )
    }

    fun appName(): String = current().name

    fun shortName(): String? = current().shortName

    fun logoText(): String? = current().logoText

    fun wordmarkPath(): String? = current().logo.wordmark

    fun markPath(): String? = current().logo.mark

    fun serviceMarkPath(): String? = current().logo.serviceMark

    fun windowIconPath(): String? = current().icons.window

    fun trayIconPath(): String? = current().icons.tray

    fun serviceIconPath(): String? = current().icons.service

    fun platformPackageIconPath(
        osType: OSType = OS.type
    ): String? {
        val icons = current().icons
        return when (osType) {
            OSType.WINDOWS -> icons.windows
            OSType.MACOS -> icons.macos
            OSType.LINUX -> icons.linux
            OSType.UNKNOWN -> null
        }
    }

    fun resourceExists(path: String?): Boolean {
        val normalized = path?.trim()?.takeIf(String::isNotBlank) ?: return false
        return app.javaClass.classLoader.getResource(normalized) != null
    }

    private fun resourcePathOrNull(configKey: String): String? {
        val value = app.config.string(configKey).trim()
        return value.takeIf(String::isNotBlank)
    }
}
