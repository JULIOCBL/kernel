package kernel.foundation

import kernel.config.ConfigLoader
import kernel.config.ConfigFile
import kernel.config.ConfigStore
import kernel.env.Env
import kernel.env.EnvLoader
import kernel.lang.LangFile
import kernel.lang.LangStore
import kernel.providers.ProviderFactory
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
    val config: ConfigStore = ConfigStore(),
    val lang: LangStore = LangStore()
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
     * Devuelve la cantidad de providers registrados sin materializar una lista.
     */
    fun providerCount(): Int = providersByType.size

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
        files.forEach { file -> loadConfig(file) }
        return this
    }

    /**
     * Carga una coleccion de archivos de configuracion respetando el orden
     * recibido.
     */
    fun loadConfig(files: Iterable<ConfigFile>): Application {
        files.forEach { file -> loadConfig(file) }
        return this
    }

    /**
     * Carga un archivo de idioma definido como codigo Kotlin.
     */
    fun loadLang(file: LangFile): Application {
        lang.load(file)
        return this
    }

    /**
     * Carga multiples archivos de idioma respetando el orden recibido.
     */
    fun loadLang(vararg files: LangFile): Application {
        files.forEach { file -> loadLang(file) }
        return this
    }

    /**
     * Carga una coleccion de archivos de idioma respetando el orden recibido.
     */
    fun loadLang(files: Iterable<LangFile>): Application {
        files.forEach { file -> loadLang(file) }
        return this
    }

    /**
     * Registra esta instancia como runtime global del proceso.
     *
     * Esto habilita helpers ergonomicos como `app()`, `config()` y `env()`
     * durante el resto del bootstrap, incluyendo providers que quieran usarlos
     * en `register()` o `boot()`.
     */
    fun initializeRuntime(): Application {
        return ApplicationRuntime.initialize(this)
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
     *
     * La variante tipada permite detectar duplicados antes de construir el
     * provider, siempre que la lambda mantenga un tipo concreto de retorno.
     */
    inline fun <reified T : ServiceProvider> register(
        noinline factory: (Application) -> T
    ): Application {
        if (hasProvider(T::class)) {
            return this
        }

        return register(factory(this))
    }

    /**
     * Registra un provider a partir de un factory declarativo con metadatos.
     *
     * A diferencia del overload basado solo en lambda, aqui podemos evitar la
     * construccion del provider si su tipo ya estaba registrado.
     */
    fun register(factory: ProviderFactory): Application {
        if (hasProvider(factory.type)) {
            return this
        }

        return register(factory.create(this))
    }

    /**
     * Registra multiples providers a partir de factories declarativos.
     *
     * Es util para bootstraps de aplicacion que quieren mantener una lista
     * central de providers estilo Laravel.
     */
    fun registerAll(vararg factories: ProviderFactory): Application {
        return registerAll(factories.asList())
    }

    /**
     * Registra una coleccion de factories de providers.
     */
    fun registerAll(factories: Iterable<ProviderFactory>): Application {
        factories.forEach { factory -> register(factory) }
        return this
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
            systemValues: Map<String, String> = System.getenv(),
            processLockMode: ProcessLockMode = ProcessLockMode.ENFORCE
        ): Application {
            val normalizedBasePath = basePath.toAbsolutePath().normalize()
            val processId = ApplicationProcessLock.acquire(normalizedBasePath, processLockMode)
            val loadedEnvironment = EnvLoader(normalizedBasePath.resolve(environmentFile)).load()

            return Application(
                basePath = normalizedBasePath,
                env = Env(loadedEnvironment, systemValues),
                config = ConfigStore()
            ).apply {
                config.set("runtime.process.pid", processId)
                config.set("runtime.process.pid_file", ApplicationProcessLock.pidFile(normalizedBasePath).toString())
                config.set("runtime.process.lock_mode", processLockMode.name.lowercase())
            }
        }

        /**
         * Crea una aplicacion bootstrappeada y la registra como runtime global.
         *
         * Este es el camino recomendado para apps desktop/CLI que operan con una
         * sola `Application` por proceso y quieren usar helpers globales de
         * forma segura desde el arranque.
         *
         * No debe usarse como sustituto de una fabrica reusable. Si el flujo
         * necesita construir dos apps distintas en el mismo proceso, usa
         * `bootstrap(...)` y trabaja explicitamente con la instancia.
         */
        fun bootstrapRuntime(
            basePath: Path,
            environmentFile: String = ".env",
            systemValues: Map<String, String> = System.getenv(),
            processLockMode: ProcessLockMode = ProcessLockMode.ENFORCE
        ): Application {
            return bootstrap(
                basePath = basePath,
                environmentFile = environmentFile,
                systemValues = systemValues,
                processLockMode = processLockMode
            ).initializeRuntime()
        }
    }
}
