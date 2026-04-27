package kernel.foundation

/**
 * Runtime global del proceso para exponer una unica aplicacion bootstrappeada.
 *
 * A diferencia de un "contexto activo", este runtime no cambia segun el hilo
 * ni por scopes temporales. Se inicializa una sola vez durante el arranque del
 * proceso y despues actua como referencia estable para helpers ergonomicos como
 * `config()` o `env()`.
 */
object ApplicationRuntime {
    private val lock = Any()

    @Volatile
    private var application: Application? = null

    /**
     * Registra la aplicacion global del proceso.
     *
     * La inicializacion es idempotente para la misma instancia, pero rechaza
     * silencios peligrosos como reemplazar la aplicacion global por otra.
     */
    fun initialize(application: Application): Application {
        synchronized(lock) {
            val current = this.application

            when {
                current == null -> this.application = application
                current !== application -> {
                    error(
                        "ApplicationRuntime ya fue inicializado con otra instancia de Application. " +
                            "Usa una sola app global por proceso o trabaja explicitamente con la instancia."
                    )
                }
            }
        }

        return application
    }

    fun isInitialized(): Boolean = application != null

    fun current(): Application {
        return application
            ?: error(
                "ApplicationRuntime no ha sido inicializado. " +
                    "Inicializa la Application durante el bootstrap antes de usar helpers globales."
            )
    }

    internal fun resetForTests() {
        synchronized(lock) {
            application = null
        }
    }
}
