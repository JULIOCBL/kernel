package kernel.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigStoreRuntimeOverrideTest {
    @Test
    fun `temporary overrides are visible inside the block and restored afterwards`() {
        val config = ConfigStore(
            mapOf(
                "database" to mapOf(
                    "connections" to mapOf(
                        "main" to mapOf(
                            "username" to "runtime_user",
                            "password" to "runtime_pass"
                        )
                    )
                )
            )
        )

        config.withTemporaryOverrides(
            mapOf(
                "database.connections.main.username" to "migration_user",
                "database.connections.main.password" to "migration_pass"
            )
        ) {
            assertEquals("migration_user", config.string("database.connections.main.username"))
            assertEquals("migration_pass", config.string("database.connections.main.password"))
        }

        assertEquals("runtime_user", config.string("database.connections.main.username"))
        assertEquals("runtime_pass", config.string("database.connections.main.password"))
    }

    @Test
    fun `temporary overrides can add new keys and remove them afterwards`() {
        val config = ConfigStore(
            mapOf(
                "app" to mapOf(
                    "name" to "Kernel"
                )
            )
        )

        config.withTemporaryOverrides(
            mapOf(
                "runtime.migration.active" to true
            )
        ) {
            assertTrue(config.bool("runtime.migration.active"))
        }

        assertEquals(false, config.bool("runtime.migration.active"))
    }
}
