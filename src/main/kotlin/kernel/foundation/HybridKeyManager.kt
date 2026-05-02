package kernel.foundation

import kernel.security.HardwareIdResolver
import kernel.security.KeyAssembler
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/**
 * Reconstruye llaves efimeras a partir de fragmentos repartidos entre JVM,
 * runtime nativo y derivacion local por dispositivo.
 */
object HybridKeyManager {
    private const val DEV_KEY_TEXT = "12345678901234567890123456789012"
    private val lock = Any()
    private val secureRandom = SecureRandom()

    @Volatile
    private var fragmentA: ByteArray? = null

    @Volatile
    private var fragmentFactory: (Int) -> ByteArray = { size ->
        ByteArray(size).also(secureRandom::nextBytes)
    }

    fun inject(a: ByteArray, b: ByteArray) {
        require(a.size == KeyAssembler.FRAGMENT_SIZE) {
            "El fragmento A debe tener exactamente ${KeyAssembler.FRAGMENT_SIZE} bytes; recibido: ${a.size}."
        }

        val copyA = a.copyOf()
        val previous = synchronized(lock) {
            val replaced = fragmentA
            fragmentA = copyA
            replaced
        }

        previous?.fill(0)
        KeyAssembler.injectFragmentB(b)
    }

    fun reconstructForDev(): SecretKeySpec {
        return SecretKeySpec(
            DEV_KEY_TEXT.toByteArray(StandardCharsets.UTF_8),
            "AES"
        )
    }

    fun generateFragmentA(): ByteArray {
        return fragmentFactory(KeyAssembler.FRAGMENT_SIZE)
    }

    fun generateFragmentC(deviceId: String): ByteArray {
        return sha256(deviceId.toByteArray(StandardCharsets.UTF_8))
    }

    fun reconstructReal(deviceId: String? = null): SecretKeySpec? {
        return runCatching {
            reconstructRealOrThrow(deviceId ?: HardwareIdResolver.currentId())
        }.onFailure { error ->
            System.err.println("No se pudo reconstruir la llave maestra: ${error.message}")
        }.getOrNull()
    }

    private fun reconstructRealOrThrow(deviceId: String): SecretKeySpec {
        val fragmentACopy: ByteArray
        val fragmentB: ByteArray

        synchronized(lock) {
            ensureFragmentsInitialized()
            fragmentACopy = fragmentA?.copyOf()
                ?: error("No existe un fragmento A cargado en HybridKeyManager.")
            fragmentB = KeyAssembler.consumeFragmentB()

            val nativeCopy = fragmentB.copyOf()
            try {
                KeyAssembler.injectFragmentB(nativeCopy)
            } finally {
                nativeCopy.fill(0)
            }
        }

        val fragmentC = generateFragmentC(deviceId)
        val xorBuffer = ByteArray(KeyAssembler.FRAGMENT_SIZE)

        return try {
            for (index in xorBuffer.indices) {
                xorBuffer[index] = (
                    fragmentACopy[index].toInt() xor
                        fragmentB[index].toInt() xor
                        fragmentC[index].toInt()
                    ).toByte()
            }

            val aesMaterial = sha256(xorBuffer)

            try {
                SecretKeySpec(aesMaterial, "AES")
            } finally {
                aesMaterial.fill(0)
            }
        } finally {
            xorBuffer.fill(0)
            fragmentACopy.fill(0)
            fragmentB.fill(0)
            fragmentC.fill(0)
        }
    }

    fun reconstructEfemerally(deviceId: String): SecretKeySpec {
        val fragmentACopy = synchronized(lock) {
            fragmentA?.copyOf()
        } ?: error(
            "No existe un fragmento A cargado en HybridKeyManager. " +
                "Inyecta fragmentos antes de reconstruir la llave."
        )

        val fragmentB = KeyAssembler.consumeFragmentB()
        val fragmentC = sha256(deviceId.toByteArray(StandardCharsets.UTF_8))
        val xorBuffer = ByteArray(KeyAssembler.FRAGMENT_SIZE)

        return try {
            for (index in xorBuffer.indices) {
                xorBuffer[index] = (fragmentACopy[index].toInt() xor fragmentB[index].toInt() xor fragmentC[index].toInt()).toByte()
            }

            val aesMaterial = sha256(xorBuffer)

            try {
                SecretKeySpec(aesMaterial, "AES")
            } finally {
                aesMaterial.fill(0)
            }
        } finally {
            xorBuffer.fill(0)
            fragmentACopy.fill(0)
            fragmentB.fill(0)
            fragmentC.fill(0)
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            fragmentA?.fill(0)
            fragmentA = null
        }
        fragmentFactory = { size ->
            ByteArray(size).also(secureRandom::nextBytes)
        }
        HardwareIdResolver.resetSupplierForTests()
    }

    internal fun installFragmentFactoryForTests(factory: (Int) -> ByteArray) {
        fragmentFactory = factory
    }

    private fun sha256(input: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(input)
    }

    private fun ensureFragmentsInitialized() {
        if (fragmentA != null) {
            return
        }

        val generatedA = generateFragmentA()
        val generatedB = fragmentFactory(KeyAssembler.FRAGMENT_SIZE)

        val previous = fragmentA
        fragmentA = generatedA
        previous?.fill(0)

        try {
            KeyAssembler.injectFragmentB(generatedB)
        } finally {
            generatedB.fill(0)
        }
    }
}
