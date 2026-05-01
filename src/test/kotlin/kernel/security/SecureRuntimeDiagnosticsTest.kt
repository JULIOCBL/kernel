package kernel.security

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SecureRuntimeDiagnosticsTest {
    @AfterTest
    fun tearDown() {
        NativeLibraryLoader.resetForTests()
    }

    @Test
    fun `reports missing resource without exposing native operations`() {
        NativeLibraryLoader.resourceOpener = { null }

        val status = SecureRuntimeDiagnostics.currentStatus()

        assertNotNull(status.resourcePath)
        assertEquals(false, status.resourcePresent)
        assertEquals(false, status.nativeLoaded)
        assertEquals(32, status.fragmentSize)
        assertTrue(status.loadError != null)
    }
}
