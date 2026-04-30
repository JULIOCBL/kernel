package kernel.providers
import kernel.foundation.Application
import kernel.foundation.SingleInstanceHandler
import kernel.routing.DesktopRouter

class DesktopRouteServiceProvider(app: Application) : ServiceProvider(app) {

    override fun register() {
        // Registramos el router en el ConfigStore para acceso global
        app.config.set("services.router", DesktopRouter())
    }

    override fun boot() {
        val router = app.config.get("services.router") as DesktopRouter

        // El handler usa el nombre de la app para el archivo de bloqueo (.lock)
        val handler = SingleInstanceHandler("kernel-playground")

        // Intentamos tomar el control como instancia primaria
        val isPrimary = handler.isPrimaryInstance { newUrl ->
            // NAVEGACIÓN EN CALIENTE: La app ya estaba abierta
            val result = router.resolve(newUrl)
            app.config.set("runtime.current_view", result)
            // FYI: Aquí podrías disparar un evento nativo para enfocar la ventana
        }

        if (isPrimary) {
            // NAVEGACIÓN EN FRÍO: Procesar link de arranque si existe
            handleInitialDeepLink(router)
        }
    }

    private fun handleInitialDeepLink(router: DesktopRouter) {
        // Recuperamos los argumentos de la JVM
        val args = System.getProperty("sun.java.command")?.split(" ") ?: emptyList()

        // Buscamos si algún argumento coincide con nuestro esquema
        val deepLink = args.find { it.startsWith("myapp://") }

        deepLink?.let { uri ->
            val result = router.resolve(uri)
            // Guardamos la vista inicial para que el entry point sepa qué cargar
            app.config.set("runtime.initial_view", result)
        }
    }
}