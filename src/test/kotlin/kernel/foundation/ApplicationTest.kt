package kernel.foundation

import kernel.config.ConfigFile
import kernel.config.MapConfigLoader
import kernel.env.Env
import kernel.providers.ServiceProvider
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationTest {
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
}
