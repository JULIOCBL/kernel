package kernel.security

/**
 * Punto de entrada seguro para el fragmento B residente en runtime nativo.
 */
object KeyAssembler {
    const val FRAGMENT_SIZE: Int = 32

    fun injectFragmentB(fragment: ByteArray) {
        require(fragment.size == FRAGMENT_SIZE) {
            "El fragmento B debe tener exactamente $FRAGMENT_SIZE bytes; recibido: ${fragment.size}."
        }

        SecureRuntime.shared.injectFragmentB(fragment.copyOf())
    }

    fun consumeFragmentB(): ByteArray {
        val fragment = try {
            SecureRuntime.shared.getFragmentB()
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
}
