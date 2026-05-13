package kernel.database

import kernel.foundation.Application
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatabaseLifecycleTest {
    @Test
    fun `closeDatabaseManager closes active pools without forcing creation`() {
        val fakeDriver = FakeDriver()
        DriverManager.registerDriver(fakeDriver)

        val application = buildApplication()
        application.loadConfig(
            "database",
            mapOf(
                "default" to "main",
                "connections" to mapOf(
                    "main" to mapOf(
                        "driver" to "pgsql",
                        "jdbcDriver" to FakeDriver::class.java.name,
                        "url" to "jdbc:kernel-test:main",
                        "pool" to mapOf(
                            "enabled" to true,
                            "minimumIdle" to 0,
                            "maximumPoolSize" to 1
                        )
                    )
                )
            )
        )

        try {
            val manager = kernel.database.pdo.connections.DatabaseManager.from(application)
            manager.withConnection { }
            assertEquals(setOf("main"), manager.activePooledConnectionNames())

            (application.config.get("services.database.manager") as? AutoCloseable)?.close()
            assertTrue(manager.activePooledConnectionNames().isEmpty())
        } finally {
            DriverManager.deregisterDriver(fakeDriver)
        }
    }

    @Test
    fun `shutdown hook registration is idempotent and removable`() {
        val addCount = AtomicInteger(0)
        val removeCount = AtomicInteger(0)
        val application = buildApplication()

        val registration = DatabaseShutdownHook(
            application = application,
            registerHook = { addCount.incrementAndGet() },
            removeHook = { removeCount.incrementAndGet() }
        )

        registration.register()
        registration.register()
        registration.close()
        registration.close()

        assertEquals(1, addCount.get())
        assertEquals(1, removeCount.get())
    }

    private fun buildApplication(): Application {
        return Application.bootstrap(
            basePath = createTempDirectory("kernel-database-lifecycle-test").toAbsolutePath(),
            systemValues = emptyMap()
        )
    }
}
