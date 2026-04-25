package kernel.foundation

/**
 * Contexto global minimo para exponer la aplicacion activa a helpers del kernel.
 *
 * Esto permite APIs estilo Laravel como `config("app.name")` sin perder la
 * opcion explicita basada en `app`.
 */
object ApplicationContext {
    @Volatile
    private var currentApplication: Application? = null

    fun bind(application: Application) {
        currentApplication = application
    }

    fun clear() {
        currentApplication = null
    }

    fun hasApplication(): Boolean = currentApplication != null

    fun current(): Application {
        return currentApplication
            ?: error(
                "No hay una aplicacion activa en ApplicationContext. " +
                    "Inicializa o registra una Application antes de usar helpers globales."
            )
    }
}
