package kernel.foundation

import kernel.security.HardwareIdResolver
import kernel.security.KeyAssembler
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HybridKeyManagerTest {
    private var injectedFragmentB: ByteArray? = null
    private var consumedFragmentB: ByteArray? = null

    @AfterTest
    fun tearDown() {
        KeyAssembler.resetBackendForTests()
        HybridKeyManager.resetForTests()
        injectedFragmentB = null
        consumedFragmentB = null
    }

    @Test
    fun `inject stores fragment a and forwards fragment b to key assembler`() {
        KeyAssembler.installBackendForTests(
            object : KeyAssembler.Backend {
                override fun inject(fragment: ByteArray) {
                    injectedFragmentB = fragment.copyOf()
                }

                override fun consume(): ByteArray {
                    return ByteArray(KeyAssembler.FRAGMENT_SIZE)
                }
            }
        )

        val fragmentA = ByteArray(KeyAssembler.FRAGMENT_SIZE) { (it + 1).toByte() }
        val fragmentB = ByteArray(KeyAssembler.FRAGMENT_SIZE) { (it + 10).toByte() }

        HybridKeyManager.inject(fragmentA, fragmentB)

        assertContentEquals(fragmentB, injectedFragmentB)
    }

    @Test
    fun `reconstruct for dev returns fixed aes key`() {
        val key = HybridKeyManager.reconstructForDev()

        assertEquals("AES", key.algorithm)
        assertContentEquals(
            "12345678901234567890123456789012".encodeToByteArray(),
            key.encoded
        )
    }

    @Test
    fun `reconstructs aes key from xor of a b and c`() {
        val fragmentA = ByteArray(KeyAssembler.FRAGMENT_SIZE) { (it + 1).toByte() }
        val fragmentB = ByteArray(KeyAssembler.FRAGMENT_SIZE) { (it + 11).toByte() }
        val deviceId = "MOCK-DEVICE-ID"

        KeyAssembler.installBackendForTests(
            object : KeyAssembler.Backend {
                override fun inject(fragment: ByteArray) {
                    injectedFragmentB = fragment
                }

                override fun consume(): ByteArray {
                    return fragmentB.copyOf().also { consumedFragmentB = it }
                }
            }
        )

        HybridKeyManager.inject(fragmentA, fragmentB)

        val expected = expectedKey(fragmentA, fragmentB, deviceId)
        val actual = HybridKeyManager.reconstructEfemerally(deviceId)

        assertEquals("AES", actual.algorithm)
        assertContentEquals(expected.encoded, actual.encoded)
    }

    @Test
    fun `reconstruct real generates fragments and rehydrates native fragment b`() {
        val fragmentA = ByteArray(KeyAssembler.FRAGMENT_SIZE) { (it + 21).toByte() }
        val fragmentB = ByteArray(KeyAssembler.FRAGMENT_SIZE) { (it + 41).toByte() }
        val generated = ArrayDeque(listOf(fragmentA.copyOf(), fragmentB.copyOf()))
        var nativeFragment = ByteArray(0)
        val deviceId = "MOCK-HARDWARE-ID"

        HybridKeyManager.installFragmentFactoryForTests { size ->
            generated.removeFirstOrNull()?.also {
                assertEquals(size, it.size)
            } ?: error("No hay mas fragmentos de prueba.")
        }
        HardwareIdResolver.installSupplierForTests {
            HardwareIdResolver.Resolution(deviceId, persistent = true)
        }
        KeyAssembler.installBackendForTests(
            object : KeyAssembler.Backend {
                override fun inject(fragment: ByteArray) {
                    injectedFragmentB = fragment.copyOf()
                    nativeFragment = fragment.copyOf()
                }

                override fun consume(): ByteArray {
                    check(nativeFragment.isNotEmpty()) {
                        "El fragmento B nativo no estaba inicializado."
                    }

                    return nativeFragment.copyOf().also {
                        consumedFragmentB = it.copyOf()
                        nativeFragment = ByteArray(0)
                    }
                }
            }
        )

        val key = HybridKeyManager.reconstructReal()

        assertEquals("AES", key!!.algorithm)
        assertContentEquals(expectedKey(fragmentA, fragmentB, deviceId).encoded, key.encoded)
        assertContentEquals(fragmentB, consumedFragmentB)
        assertContentEquals(fragmentB, injectedFragmentB)
    }

    @Test
    fun `generate fragment c returns stable sha256 bytes`() {
        val deviceId = "stable-device"
        val expected = MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray())

        assertContentEquals(expected, HybridKeyManager.generateFragmentC(deviceId))
    }

    @Test
    fun `reconstruct real returns null when hardware id is not persistent`() {
        HybridKeyManager.installFragmentFactoryForTests { size ->
            ByteArray(size) { 1 }
        }
        HardwareIdResolver.installSupplierForTests {
            HardwareIdResolver.Resolution("ephemeral-id", persistent = false)
        }
        KeyAssembler.installBackendForTests(
            object : KeyAssembler.Backend {
                override fun inject(fragment: ByteArray) = Unit
                override fun consume(): ByteArray = ByteArray(KeyAssembler.FRAGMENT_SIZE) { 2 }
            }
        )

        val key = HybridKeyManager.reconstructReal()

        kotlin.test.assertNull(key)
    }

    @Test
    fun `fails when fragment a has not been injected`() {
        KeyAssembler.installBackendForTests(
            object : KeyAssembler.Backend {
                override fun inject(fragment: ByteArray) = Unit
                override fun consume(): ByteArray = ByteArray(KeyAssembler.FRAGMENT_SIZE)
            }
        )

        val error = assertFailsWith<IllegalStateException> {
            HybridKeyManager.reconstructEfemerally("MOCK-DEVICE-ID")
        }

        kotlin.test.assertTrue(error.message!!.contains("fragmento A"))
    }

    private fun expectedKey(a: ByteArray, b: ByteArray, deviceId: String): SecretKeySpec {
        val c = MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray())
        val xor = ByteArray(KeyAssembler.FRAGMENT_SIZE)

        for (index in xor.indices) {
            xor[index] = (a[index].toInt() xor b[index].toInt() xor c[index].toInt()).toByte()
        }

        val aes = MessageDigest.getInstance("SHA-256").digest(xor)

        return SecretKeySpec(aes, "AES")
    }
}
