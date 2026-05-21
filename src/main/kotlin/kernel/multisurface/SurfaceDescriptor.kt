package kernel.multisurface

/**
 * Describe una surface abierta dentro del proceso actual.
 */
data class SurfaceDescriptor(
    val id: String,
    val role: SurfaceRole,
    val title: String,
    val targetDisplayIndex: Int = 0,
    val fullscreen: Boolean = false,
    val externalDisplayPreferred: Boolean = false,
    val visible: Boolean = true,
    val capabilities: SurfaceCapabilities = SurfaceCapabilities()
)
