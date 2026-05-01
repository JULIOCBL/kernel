package kernel.security

import kernel.foundation.OS
import kernel.foundation.OSType
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Cargador portable para la libreria nativa `ksrjcbl`.
 *
 * Extrae el binario correcto desde `/native/` a un archivo temporal y usa
 * `System.load(...)` para mantener la portabilidad del JAR.
 */
internal object NativeLibraryLoader {
    private val lock = Any()

    @Volatile
    private var loaded: Boolean = false

    internal var resourceOpener: (String) -> InputStream? = { resourcePath ->
        SecureRuntime::class.java.getResourceAsStream(resourcePath)
    }

    internal var nativeLoader: (String) -> Unit = System::load

    fun isLoaded(): Boolean = loaded

    fun ensureLoaded() {
        if (loaded) {
            return
        }

        synchronized(lock) {
            if (loaded) {
                return
            }

            val resourcePath = resourcePathFor(OS.type)
            val extractedLibrary = extract(resourcePath)

            try {
                nativeLoader(extractedLibrary.toAbsolutePath().toString())
            } catch (error: UnsatisfiedLinkError) {
                throw IllegalStateException(
                    "No se pudo cargar la libreria nativa `${extractedLibrary.fileName}`.",
                    error
                )
            }

            loaded = true
        }
    }

    internal fun resourcePathFor(osType: OSType): String {
        val fileName = when (osType) {
            OSType.MACOS -> "libksrjcbl.dylib"
            OSType.LINUX -> "libksrjcbl.so"
            OSType.WINDOWS -> "ksrjcbl.dll"
            OSType.UNKNOWN -> {
                throw IllegalStateException(
                    "Sistema operativo no soportado para Kernel Secure Runtime: `${System.getProperty("os.name")}`."
                )
            }
        }

        return "/native/$fileName"
    }

    internal fun resourceExists(resourcePath: String): Boolean {
        return resourceOpener(resourcePath)?.use { true } ?: false
    }

    private fun extract(resourcePath: String): Path {
        val fileName = resourcePath.substringAfterLast('/')
        val suffix = fileName.substringAfterLast('.', "")
            .takeIf(String::isNotBlank)
            ?.let { ".$it" }
            ?: ""
        val prefix = fileName.substringBeforeLast('.', fileName)
            .takeIf { it.length >= 3 }
            ?: "ksr"

        val input = resourceOpener(resourcePath)
            ?: throw IllegalStateException(
                "No se encontro la libreria nativa `$resourcePath` dentro de los recursos del proyecto."
            )

        input.use { stream ->
            val tempFile = Files.createTempFile(prefix, suffix)
            tempFile.toFile().deleteOnExit()
            Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING)
            return tempFile
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            loaded = false
            resourceOpener = { resourcePath ->
                SecureRuntime::class.java.getResourceAsStream(resourcePath)
            }
            nativeLoader = System::load
        }
    }
}
