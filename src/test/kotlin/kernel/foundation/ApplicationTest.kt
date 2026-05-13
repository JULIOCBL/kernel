package kernel.foundation

import kernel.config.ConfigFile
import kernel.config.MapConfigLoader
import kernel.env.Env
import kernel.lang.LangFile
import kernel.providers.ProviderFactory
import kernel.providers.ServiceProvider
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ApplicationTest {
    @BeforeTest
    fun resetRuntime() {
        ApplicationRuntime.resetForTests()
        ApplicationProcessLock.resetForTests()
        ConstructorProbeProvider.constructorCalls = 0
    }

    @Test
    fun `bootstrap loads env values from base path`() {
        val basePath = createTempDirectory("kernel-application-test").toAbsolutePath()
        basePath.resolve(".env").writeText(
            """
            APP_ENV=testing
            APP_NAME=Kernel Test
            """.trimIndent()
        )

        val application = Application.bootstrap(
            basePath = basePath,
            systemValues = emptyMap()
        )

        assertEquals(basePath, application.basePath)
        assertEquals("testing", application.environment())
        assertEquals("Kernel Test", application.env.string("APP_NAME"))
        assertEquals(basePath.resolve("config").normalize(), application.path("config"))
        assertEquals(ProcessHandle.current().pid(), application.config.get("runtime.process.pid"))
        assertTrue(Files.exists(basePath.resolve(".pid")))
    }

    @Test
    fun `bootstrap blocks when another live process already owns the base path`() {
        val basePath = createTempDirectory("kernel-process-lock-test").toAbsolutePath()
        val blocker = ProcessBuilder("sleep", "5").start()
        basePath.resolve(".pid").writeText(blocker.pid().toString())

        try {
            val error = assertFailsWith<IllegalStateException> {
                Application.bootstrap(
                    basePath = basePath,
                    systemValues = emptyMap(),
                    processLockMode = ProcessLockMode.ENFORCE
                )
            }

            assertTrue(error.message!!.contains("instancia activa"))
        } finally {
            blocker.destroyForcibly()
            ApplicationProcessLock.clear(basePath)
        }
    }

    @Test
    fun `bootstrap can observe without claiming the process lock`() {
        val basePath = createTempDirectory("kernel-process-observe-test").toAbsolutePath()
        val blocker = ProcessBuilder("sleep", "5").start()
        basePath.resolve(".pid").writeText(blocker.pid().toString())

        try {
            val application = Application.bootstrap(
                basePath = basePath,
                systemValues = emptyMap(),
                processLockMode = ProcessLockMode.OBSERVE
            )

            assertEquals(blocker.pid(), ApplicationProcessLock.readPid(basePath))
            assertEquals(ProcessLockMode.OBSERVE.name.lowercase(), application.config.string("runtime.process.lock_mode"))
        } finally {
            blocker.destroyForcibly()
            ApplicationProcessLock.clear(basePath)
        }
    }

    @Test
    fun `loads kotlin config files by namespace`() {
        val basePath = createTempDirectory("kernel-config-file-test").toAbsolutePath()
        basePath.resolve(".env").writeText("APP_ENV=testing")
        val application = Application.bootstrap(basePath = basePath, systemValues = emptyMap())

        application.loadConfig(AppConfigFile)
        application.loadConfig(FeaturesConfigFile)

        assertEquals("Kernel Test App", application.config.string("app.name"))
        assertEquals("testing", application.config.string("app.env"))
        assertTrue(application.config.bool("app.debug"))
        assertTrue(application.config.bool("features.console"))
    }

    @Test
    fun `supports short config access from application store`() {
        val basePath = createTempDirectory("kernel-config-short-app-test").toAbsolutePath()
        val application = Application.bootstrap(basePath = basePath, systemValues = emptyMap())

        application.loadConfig(AppConfigFile)

        assertEquals("Kernel Test App", application.config("app.name"))
        assertEquals(true, application.config("app.debug"))
        assertEquals("fallback", application.config("missing.key", "fallback"))
    }

    @Test
    fun `loads config and lang files from iterable catalogs`() {
        val basePath = createTempDirectory("kernel-config-lang-catalog-test").toAbsolutePath()
        val application = Application.bootstrap(basePath = basePath, systemValues = emptyMap())

        application.loadConfig(listOf(AppConfigFile, FeaturesConfigFile))
        application.loadLang(listOf(ValidationLangFile))

        assertEquals("Kernel Test App", application.config.string("app.name"))
        assertEquals(
            "El campo nombre es obligatorio.",
            application.lang.translate(
                key = "validation.required",
                locale = "es",
                replacements = mapOf("attribute" to "nombre")
            )
        )
    }

    @Test
    fun `global helpers work after runtime bootstrap`() {
        val basePath = createTempDirectory("kernel-runtime-global-test").toAbsolutePath()
        val application = Application.bootstrapRuntime(basePath = basePath, systemValues = emptyMap())
            .loadConfig(AppConfigFile)

        assertEquals(application, ApplicationRuntime.current())
        assertEquals("Kernel Test App", kernel.config.config("app.name"))
        assertEquals(true, kernel.config.config("app.debug"))
        assertEquals("Kernel From Default", ApplicationRuntime.current().env.get("MISSING_ENV", "Kernel From Default"))
        assertEquals(basePath, basePath())
        assertEquals(basePath.resolve("config").normalize(), basePath("config"))
    }

    @Test
    fun `bootstrap runtime makes global helpers available during provider registration`() {
        val basePath = createTempDirectory("kernel-runtime-provider-test").toAbsolutePath()

        val application = Application.bootstrapRuntime(
            basePath = basePath,
            systemValues = emptyMap()
        ).register(::RuntimeAwareProvider)
            .boot()

        assertTrue(application.config.bool("providers.runtimeAware.registered"))
        assertTrue(application.config.bool("providers.runtimeAware.booted"))
    }

    @Test
    fun `environment values remain explicit on the application instance`() {
        val basePath = createTempDirectory("kernel-env-helper-test").toAbsolutePath()
        val application = Application.bootstrap(
            basePath = basePath,
            systemValues = mapOf("APP_NAME" to "Kernel From Env")
        )

        assertEquals("Kernel From Env", application.env.get("APP_NAME"))
        assertEquals("fallback", application.env.get("MISSING_ENV", "fallback"))
    }

    @Test
    fun `runtime initialization rejects replacing the process application`() {
        ApplicationRuntime.resetForTests()
        val firstApplication = Application.bootstrap(
            basePath = createTempDirectory("kernel-context-first-test").toAbsolutePath(),
            systemValues = emptyMap()
        ).loadConfig("app", mapOf("name" to "Kernel First"))
        val secondApplication = Application.bootstrap(
            basePath = createTempDirectory("kernel-context-second-test").toAbsolutePath(),
            systemValues = emptyMap()
        ).loadConfig("app", mapOf("name" to "Kernel Second"))

        ApplicationRuntime.initialize(firstApplication)

        val error = assertFailsWith<IllegalStateException> {
            ApplicationRuntime.initialize(secondApplication)
        }

        assertEquals("Kernel First", firstApplication.config.string("app.name"))
        assertEquals("Kernel Second", secondApplication.config.string("app.name"))
        assertFalse(firstApplication === secondApplication)
        assertTrue(error.message!!.contains("ya fue inicializado"))
    }

    @Test
    fun `global helpers fail before runtime bootstrap`() {
        ApplicationRuntime.resetForTests()

        val configError = assertFailsWith<IllegalStateException> {
            kernel.config.config("app.name")
        }

        assertTrue(configError.message!!.contains("no ha sido inicializado"))
    }

    @Test
    fun `registering and booting providers applies lifecycle once`() {
        val application = Application.bootstrap(
            basePath = createTempDirectory("kernel-provider-test").toAbsolutePath(),
            systemValues = emptyMap()
        )

        application.loadConfig(
            loader = MapConfigLoader(mapOf("app" to mapOf("name" to "Kernel Test App")))
        )
        application.register(::TrackingProvider)
        application.register(::TrackingProvider)
        application.boot()

        assertTrue(application.config.bool("providers.tracking.registered"))
        assertTrue(application.config.bool("providers.tracking.booted"))
        assertEquals(1, application.config.int("providers.tracking.registerCalls"))
        assertEquals(1, application.config.int("providers.tracking.bootCalls"))
        assertEquals(1, application.providers().size)
        assertTrue(application.isBooted())
        assertTrue(application.hasProvider(TrackingProvider::class))
        assertEquals("Kernel Test App", application.config.string("app.name"))
    }

    @Test
    fun `register all supports central provider lists`() {
        val application = Application.bootstrap(
            basePath = createTempDirectory("kernel-provider-list-test").toAbsolutePath(),
            systemValues = emptyMap()
        )
        val providers: List<ProviderFactory> = listOf(
            ProviderFactory(TrackingProvider::class, ::TrackingProvider),
            ProviderFactory(TrackingProvider::class, ::TrackingProvider),
            ProviderFactory(MetricsProvider::class, ::MetricsProvider)
        )

        application.registerAll(providers).boot()

        assertTrue(application.config.bool("providers.tracking.registered"))
        assertTrue(application.config.bool("providers.metrics.registered"))
        assertTrue(application.config.bool("providers.metrics.booted"))
        assertEquals(2, application.providers().size)
    }

    @Test
    fun `register all does not instantiate duplicate providers`() {
        val application = Application.bootstrap(
            basePath = createTempDirectory("kernel-provider-dedup-test").toAbsolutePath(),
            systemValues = emptyMap()
        )
        val providers = listOf(
            ProviderFactory(ConstructorProbeProvider::class, ::ConstructorProbeProvider),
            ProviderFactory(ConstructorProbeProvider::class, ::ConstructorProbeProvider)
        )

        application.registerAll(providers).boot()

        assertEquals(1, ConstructorProbeProvider.constructorCalls)
        assertEquals(1, application.providers().size)
    }

    @Test
    fun `typed register does not instantiate duplicate providers`() {
        val application = Application.bootstrap(
            basePath = createTempDirectory("kernel-provider-single-dedup-test").toAbsolutePath(),
            systemValues = emptyMap()
        )

        application.register(::ConstructorProbeProvider)
        application.register(::ConstructorProbeProvider)

        assertEquals(1, ConstructorProbeProvider.constructorCalls)
        assertEquals(1, application.providers().size)
    }

    @Test
    fun `provider registered after boot is booted immediately`() {
        val application = Application.bootstrap(
            basePath = createTempDirectory("kernel-provider-boot-test").toAbsolutePath(),
            systemValues = emptyMap()
        )

        assertFalse(application.isBooted())

        application.boot()
        application.register(::LateProvider)

        assertTrue(application.config.bool("providers.late.registered"))
        assertTrue(application.config.bool("providers.late.booted"))
    }

    private class TrackingProvider(app: Application) : ServiceProvider(app) {
        override fun register() {
            app.config.set("providers.tracking.registered", true)
            app.config.set(
                "providers.tracking.registerCalls",
                app.config.int("providers.tracking.registerCalls") + 1
            )
        }

        override fun boot() {
            app.config.set("providers.tracking.booted", true)
            app.config.set(
                "providers.tracking.bootCalls",
                app.config.int("providers.tracking.bootCalls") + 1
            )
        }
    }

    private class MetricsProvider(app: Application) : ServiceProvider(app) {
        override fun register() {
            app.config.set("providers.metrics.registered", true)
        }

        override fun boot() {
            app.config.set("providers.metrics.booted", true)
        }
    }

    private class RuntimeAwareProvider(app: Application) : ServiceProvider(app) {
        override fun register() {
            ApplicationRuntime.current().config.set("providers.runtimeAware.registered", true)
        }

        override fun boot() {
            ApplicationRuntime.current().config.set("providers.runtimeAware.booted", true)
        }
    }

    private class ConstructorProbeProvider(app: Application) : ServiceProvider(app) {
        init {
            constructorCalls += 1
        }

        companion object {
            var constructorCalls: Int = 0
        }
    }

    private class LateProvider(app: Application) : ServiceProvider(app) {
        override fun register() {
            app.config.set("providers.late.registered", true)
        }

        override fun boot() {
            app.config.set("providers.late.booted", true)
        }
    }

    private object AppConfigFile : ConfigFile {
        override val namespace: String = "app"

        override fun load(env: Env): Map<String, Any?> {
            return mapOf(
                "name" to "Kernel Test App",
                "env" to env.string("APP_ENV", "local"),
                "debug" to true
            )
        }
    }

    private object FeaturesConfigFile : ConfigFile {
        override val namespace: String = "features"

        override fun load(env: Env): Map<String, Any?> {
            return mapOf(
                "console" to true,
                "migrations" to env.bool("ENABLE_MIGRATIONS", true)
            )
        }
    }

    private object ValidationLangFile : LangFile {
        override val locale: String = "es"
        override val namespace: String = "validation"

        override fun load(): Map<String, Any?> {
            return mapOf(
                "required" to "El campo :attribute es obligatorio."
            )
        }
    }
}
