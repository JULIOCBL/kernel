package kernel.database.migrations

import kernel.database.pdo.drivers.MariaDbDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MigrationConfigurationFeatureTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `migration defaults to app connection resolution and transactional execution`() {
        val migration = object : Migration() {
            override fun up() {
                create("default_config_entries") {
                    id().primaryKey()
                }
            }

            override fun down() {
                dropIfExists("default_config_entries")
            }
        }

        assertNull(migration.connectionName)
        assertTrue(migration.withinTransaction)
        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS default_config_entries (
                id UUID NOT NULL,
                PRIMARY KEY (id)
            );
            """.trimIndent(),
            generator.generateUp(migration)
        )
    }

    @Test
    fun `migration can pin a connection and disable schema transactions`() {
        val migration = object : Migration() {
            override val connectionName: String = "mariadb"
            override val withinTransaction: Boolean = false

            override fun up() {
                create("driver_pinned_entries") {
                    uuid("id").generatedUuid().primaryKey()
                    string("label").notNull()
                }
            }

            override fun down() {
                dropIfExists("driver_pinned_entries")
            }
        }

        assertEquals("mariadb", migration.connectionName)
        assertFalse(migration.withinTransaction)
        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS driver_pinned_entries (
                id CHAR(36) NOT NULL DEFAULT UUID(),
                label VARCHAR(255) NOT NULL,
                PRIMARY KEY (id)
            );
            """.trimIndent(),
            generator.generateUp(migration, MariaDbDriver)
        )
    }
}
