package kernel.foundation

import kernel.config.ConfigLoader
import kernel.config.ConfigFile
import kernel.config.ConfigStore
import kernel.env.Env
import kernel.env.EnvLoader
import kernel.providers.ServiceProvider
import java.nio.file.Path
import kotlin.reflect.KClass

/**
 * Punto central de arranque para aplicaciones que consumen el kernel.
 *
 * La aplicacion conoce su ruta base, variables de entorno, configuracion y
 * providers registrados. En pasos posteriores servira tambien como entrada al
 * container, kernels de consola/HTTP y otros servicios del framework.
 */
class Application(
    val basePath: Path,
    val env: Env = Env(),
    val config: ConfigStore = ConfigStore()
) {
    private val providersByType = linkedMapOf<KClass<out ServiceProvider>, ServiceProvider>()
    private var booted: Boolean = false

    /**
     * Resuelve una ruta relativa a la raiz de la aplicacion.
     */
    fun path(relativePath: String = ""): Path {
        if (relativePath.isBlank()) {
            return basePath
        }

        return basePath.resolve(relativePath).normalize()
    }

    /**
     * Devuelve el entorno actual de la aplicacion.
     *
     * La configuracion tiene prioridad para permitir overrides explicitos desde
     * providers o archivos de config; si no existe, usa `APP_ENV`.
     */
    fun environment(default: String = "production"): String {
        val configuredEnvironment = config.string("app.env")

        return if (configuredEnvironment.isNotBlank()) {
            configuredEnvironment
        } else {
            env.string("APP_ENV", default)
        }
    }

    /**
     * Indica si la aplicacion ya ejecuto la fase de boot.
     */
    fun isBooted(): Boolean = booted

    /**
     * Devuelve una copia de los providers registrados.
     */
    fun providers(): List<ServiceProvider> = providersByType.values.toList()

    /**
     * Carga configuracion en memoria usando un loader desacoplado del origen.
     */
    fun loadConfig(loader: ConfigLoader, namespace: String = ""): Application {
        config.merge(namespace, loader.load())
        return this
    }

    /**
     * Carga configuracion materializada directamente como mapa.
     */
    fun loadConfig(namespace: String, values: Map<String, Any?>): Application {
        config.merge(namespace, values)
        return this
    }

    /**
     * Carga un archivo de configuracion definido como codigo Kotlin.
     */
    fun loadConfig(file: ConfigFile): Application {
        return loadConfig(file.namespace, file.load(env))
    }

    /**
     * Carga multiples archivos de configuracion respetando el orden recibido.
     */
    fun loadConfig(vararg files: ConfigFile): Application {
        files.forEach(::loadConfig)
        return this
    }

    /**
     * Registra un provider por instancia.
     *
     * El metodo ignora duplicados por tipo para mantener un ciclo de vida
     * predecible. Si la aplicacion ya esta booted, el nuevo provider se bootea
     * inmediatamente despues de registrarse.
     */
    fun register(provider: ServiceProvider): Application {
        val providerType = provider::class

        if (providersByType.containsKey(providerType)) {
            return this
        }

        providersByType[providerType] = provider
        provider.register()

        if (booted) {
            provider.boot()
        }

        return this
    }

    /**
     * Registra un provider usando un factory basado en la aplicacion actual.
     */
    fun register(factory: (Application) -> ServiceProvider): Application {
        return register(factory(this))
    }

    /**
     * Ejecuta la fase de boot de todos los providers registrados.
     */
    fun boot(): Application {
        if (booted) {
            return this
        }

        providersByType.values.forEach(ServiceProvider::boot)
        booted = true

        return this
    }

    /**
     * Indica si un tipo de provider ya esta registrado.
     */
    fun hasProvider(type: KClass<out ServiceProvider>): Boolean {
        return providersByType.containsKey(type)
    }

    companion object {
        /**
         * Crea una aplicacion bootstrappeada desde una ruta base.
         *
         * Por ahora solo materializa `.env`; la carga automatica de config y
         * kernels se agregara en siguientes pasos.
         */
        fun bootstrap(
            basePath: Path,
            environmentFile: String = ".env",
            systemValues: Map<String, String> = System.getenv()
        ): Application {
            val normalizedBasePath = basePath.toAbsolutePath().normalize()
            val loadedEnvironment = EnvLoader(normalizedBasePath.resolve(environmentFile)).load()

            return Application(
                basePath = normalizedBasePath,
                env = Env(loadedEnvironment, systemValues),
                config = ConfigStore()
            )
        }
    }
}
