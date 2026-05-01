package kernel.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeyAssemblerTest {
    @Test
    fun `rejects fragment b with invalid size`() {
        val error = assertFailsWith<IllegalArgumentException> {
            KeyAssembler.injectFragmentB(ByteArray(31))
        }

        assertTrue(error.message!!.contains("exactamente 32 bytes"))
    }

    @Test
    fun `exposes fragment size constant`() {
        assertEquals(32, KeyAssembler.FRAGMENT_SIZE)
    }
}
