package kernel.branding

data class AppBranding(
    val name: String,
    val shortName: String? = null,
    val logoText: String? = null,
    val logo: BrandingLogoSet = BrandingLogoSet(),
    val icons: AppIconSet = AppIconSet()
)
