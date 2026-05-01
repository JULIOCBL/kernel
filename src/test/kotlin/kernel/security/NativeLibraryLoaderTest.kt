package kernel.security

import kernel.foundation.OS
import kernel.foundation.OSType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NativeLibraryLoaderTest {
    @AfterTest
    fun tearDown() {
        NativeLibraryLoader.resetForTests()
    }

    @Test
    fun `maps supported operating systems to native resource path`() {
        assertEquals("/native/libksrjcbl.dylib", NativeLibraryLoader.resourcePathFor(OSType.MACOS))
        assertEquals("/native/libksrjcbl.so", NativeLibraryLoader.resourcePathFor(OSType.LINUX))
        assertEquals("/native/ksrjcbl.dll", NativeLibraryLoader.resourcePathFor(OSType.WINDOWS))
    }

    @Test
    fun `fails fast for unsupported operating systems`() {
        val error = assertFailsWith<IllegalStateException> {
            NativeLibraryLoader.resourcePathFor(OSType.UNKNOWN)
        }

        assertTrue(error.message!!.contains("Sistema operativo no soportado"))
    }

    @Test
    fun `fails when native resource is missing`() {
        NativeLibraryLoader.resourceOpener = { null }

        val error = assertFailsWith<IllegalStateException> {
            NativeLibraryLoader.ensureLoaded()
        }

        assertTrue(error.message!!.contains("No se encontro la libreria nativa"))
    }

    @Test
    fun `loads bundled native library on current macos environment`() {
        if (!OS.isMac) {
            return
        }

        NativeLibraryLoader.ensureLoaded()
    }
}
