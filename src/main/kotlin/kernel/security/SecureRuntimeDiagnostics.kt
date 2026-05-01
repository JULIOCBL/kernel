package kernel.security

import kernel.foundation.OS

data class SecureRuntimeStatus(
    val osName: String,
    val resourcePath: String?,
    val resourcePresent: Boolean,
    val nativeLoaded: Boolean,
    val fragmentSize: Int,
    val loadError: String? = null
) {
    val loadable: Boolean
        get() = nativeLoaded && loadError == null
}

/**
 * Diagnostico seguro del runtime nativo.
 *
 * No expone fragmentos ni permite inyectar/consumir llaves; solo informa si el
 * binario esperado existe y si la libreria pudo cargarse en el proceso actual.
 */
object SecureRuntimeDiagnostics {
    fun currentStatus(): SecureRuntimeStatus {
        val resourcePath = runCatching {
            NativeLibraryLoader.resourcePathFor(OS.type)
        }.getOrNull()

        if (resourcePath == null) {
            return SecureRuntimeStatus(
                osName = System.getProperty("os.name"),
                resourcePath = null,
                resourcePresent = false,
                nativeLoaded = false,
                fragmentSize = KeyAssembler.FRAGMENT_SIZE,
                loadError = "Sistema operativo no soportado."
            )
        }

        val present = NativeLibraryLoader.resourceExists(resourcePath)
        val loadError = runCatching {
            NativeLibraryLoader.ensureLoaded()
        }.exceptionOrNull()?.message

        return SecureRuntimeStatus(
            osName = System.getProperty("os.name"),
            resourcePath = resourcePath,
            resourcePresent = present,
            nativeLoaded = NativeLibraryLoader.isLoaded(),
            fragmentSize = KeyAssembler.FRAGMENT_SIZE,
            loadError = loadError
        )
    }
}
