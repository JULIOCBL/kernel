package kernel.foundation

enum class OSType {
    WINDOWS, MACOS, LINUX, UNKNOWN
}
object OS {
    private val name = System.getProperty("os.name").lowercase()

    val type: OSType = when {
        name.contains("win") -> OSType.WINDOWS
        name.contains("mac") -> OSType.MACOS
        name.contains("nix") || name.contains("nux") || name.contains("aix") -> OSType.LINUX
        else -> OSType.UNKNOWN
    }

    val isWindows: Boolean = type == OSType.WINDOWS
    val isMac: Boolean = type == OSType.MACOS
    val isLinux: Boolean = type == OSType.LINUX

    /**
     * Retorna la ruta para registrar el esquema de URL en Linux (XDG).
     */
    fun getLinuxApplicationsPath(): String {
        return "${System.getProperty("user.home")}/.local/share/applications"
    }
}