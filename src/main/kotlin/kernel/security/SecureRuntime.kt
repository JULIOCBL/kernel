package kernel.security

/**
 * Puente JNI hacia Kernel Secure Runtime (Rust).
 *
 * La libreria nativa se carga de forma diferida desde recursos antes de la
 * primera llamada real al runtime.
 */
class SecureRuntime private constructor() {
    init {
        NativeLibraryLoader.ensureLoaded()
    }

    external fun injectFragmentB(fragment: ByteArray)

    external fun getFragmentB(): ByteArray

    companion object {
        val shared: SecureRuntime by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            SecureRuntime()
        }
    }
}
