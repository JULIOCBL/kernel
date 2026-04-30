package kernel.database.migrations

import kernel.database.pdo.drivers.MariaDbDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MariaDbGrammarTest {
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
                    binary("payload")
                    jsonb("metadata").notNull().defaultRaw("'{}'::jsonb")
                    timestampTz("created_at", precision = 6).notNull().defaultCurrentTimestamp()
                }
            }

            override fun down() = Unit
        }

        val sql = generator.generateUp(migration, MariaDbDriver)

        assertTrue(sql.contains("id CHAR(36) NOT NULL DEFAULT UUID()"), sql)
        assertTrue(sql.contains("sequence BIGINT AUTO_INCREMENT NOT NULL"), sql)
        assertTrue(sql.contains("payload LONGBLOB"), sql)
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

    @Test
    fun `supports mariadb specific numeric text and enum types`() {
        val migration = object : Migration() {
            override fun up() {
                create("mariadb_catalog") {
                    tinyInteger("tiny_score").notNull()
                    mediumInteger("medium_score")
                    double("rating")
                    year("release_year")
                    mediumText("notes")
                    enumValues("status", "draft", "published").notNull()
                    setValues("channels", "web", "mobile")
                }
            }

            override fun down() = Unit
        }

        val sql = generator.generateUp(migration, MariaDbDriver)

        assertTrue(sql.contains("tiny_score TINYINT NOT NULL"), sql)
        assertTrue(sql.contains("medium_score MEDIUMINT"), sql)
        assertTrue(sql.contains("rating DOUBLE"), sql)
        assertTrue(sql.contains("release_year YEAR"), sql)
        assertTrue(sql.contains("notes MEDIUMTEXT"), sql)
        assertTrue(sql.contains("status ENUM('draft', 'published') NOT NULL"), sql)
        assertTrue(sql.contains("channels SET('web', 'mobile')"), sql)
    }

    @Test
    fun `translates drop index statements to mariadb table syntax`() {
        val migration = object : Migration() {
            override fun up() = Unit

            override fun down() {
                table("orders") {
                    dropIndex("orders_customer_id_index")
                }
                dropIndex("orders_status_created_index", table = "orders")
            }
        }

        val statements = generator.generateDownStatements(migration, MariaDbDriver)

        assertEquals(
            listOf(
                "DROP INDEX IF EXISTS orders_customer_id_index ON orders;",
                "DROP INDEX IF EXISTS orders_status_created_index ON orders;"
            ),
            statements
        )
    }

    @Test
    fun `rejects top level mariadb drop index without table hint`() {
        val migration = object : Migration() {
            override fun up() = Unit

            override fun down() {
                dropIndex("orders_status_index")
            }
        }

        val error = assertFailsWith<IllegalArgumentException> {
            generator.generateDown(migration, MariaDbDriver)
        }

        assertTrue(error.message.orEmpty().contains("requiere el nombre de la tabla"))
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
