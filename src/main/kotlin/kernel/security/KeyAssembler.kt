package kernel.security

/**
 * Punto de entrada seguro para el fragmento B residente en runtime nativo.
 */
object KeyAssembler {
    const val FRAGMENT_SIZE: Int = 32

    @Volatile
    private var backend: Backend = object : Backend {
        override fun inject(fragment: ByteArray) {
            SecureRuntime.shared.injectFragmentB(fragment)
        }

        override fun consume(): ByteArray {
            return SecureRuntime.shared.getFragmentB()
        }
    }

    fun injectFragmentB(fragment: ByteArray) {
        require(fragment.size == FRAGMENT_SIZE) {
            "El fragmento B debe tener exactamente $FRAGMENT_SIZE bytes; recibido: ${fragment.size}."
        }

        backend.inject(fragment.copyOf())
    }

    fun consumeFragmentB(): ByteArray {
        val fragment = try {
            backend.consume()
        } catch (error: IllegalStateException) {
            throw error
        } catch (error: RuntimeException) {
            throw IllegalStateException("No se pudo recuperar el fragmento B desde el runtime nativo.", error)
        }

        check(fragment.size == FRAGMENT_SIZE) {
            "El runtime nativo devolvio un fragmento B invalido de ${fragment.size} bytes."
        }

        return fragment
    }

    internal fun installBackendForTests(backend: Backend) {
        this.backend = backend
    }

    internal fun resetBackendForTests() {
        backend = object : Backend {
            override fun inject(fragment: ByteArray) {
                SecureRuntime.shared.injectFragmentB(fragment)
            }

            override fun consume(): ByteArray {
                return SecureRuntime.shared.getFragmentB()
            }
        }
    }

    internal interface Backend {
        fun inject(fragment: ByteArray)
        fun consume(): ByteArray
    }
}
