package kernel.database.migrations

import kernel.database.pdo.drivers.MariaDbDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MariaDbMigrationSqlGeneratorTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `translates common create table migrations to mariadb`() {
        val sql = generator.generateUp(
            playgroundRuntimeProbeMigration(),
            MariaDbDriver
        )

        assertEquals(
            """
            CREATE TABLE IF NOT EXISTS playground_runtime_probe (
                id CHAR(36) NOT NULL,
                label VARCHAR(120) NOT NULL,
                created_at TIMESTAMP,
                updated_at TIMESTAMP,
                PRIMARY KEY (id)
            );
            """.trimIndent(),
            sql
        )
    }

    @Test
    fun `translates postgres defaults and auto increment types to mariadb`() {
        val migration = object : Migration() {
            override fun up() {
                create("users") {
                    uuid("id").generatedUuid().primaryKey()
                    bigIncrements("sequence")
                    jsonb("metadata").notNull().defaultRaw("'{}'::jsonb")
                    timestampTz("created_at", precision = 6).notNull().defaultCurrentTimestamp()
                }
            }

            override fun down() = Unit
        }

        val sql = generator.generateUp(migration, MariaDbDriver)

        assertTrue(sql.contains("id CHAR(36) NOT NULL DEFAULT UUID()"), sql)
        assertTrue(sql.contains("sequence BIGINT AUTO_INCREMENT NOT NULL"), sql)
        assertTrue(sql.contains("metadata JSON NOT NULL DEFAULT '{}'"), sql)
        assertTrue(sql.contains("created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP"), sql)
    }

    @Test
    fun `fails fast on postgres only statements for mariadb`() {
        val migration = object : Migration() {
            override fun up() {
                createExtension("pgcrypto")
            }

            override fun down() = Unit
        }

        val error = assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration, MariaDbDriver)
        }

        assertTrue(error.message.orEmpty().contains("MariaDB"))
    }

    private fun playgroundRuntimeProbeMigration(): Migration {
        return object : Migration() {
            override fun up() {
                create("playground_runtime_probe") {
                    id().primaryKey()
                    string("label", 120).notNull()
                    timestampsTz()
                }
            }

            override fun down() {
                dropIfExists("playground_runtime_probe")
            }
        }
    }
}
