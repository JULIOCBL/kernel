package kernel.database.migrations

import kernel.database.pdo.drivers.PostgreSqlDriver
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PostgresGrammarTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `translates portable double and datetime aliases to postgres sql`() {
        val migration = object : Migration() {
            override fun up() {
                create("postgres_measurements") {
                    increments("sequence")
                    binary("payload")
                    double("value")
                    dateTime("registered_at", precision = 3)
                }
            }

            override fun down() = Unit
        }

        val sql = generator.generateUp(migration, PostgreSqlDriver)

        assertTrue(sql.contains("sequence SERIAL NOT NULL"), sql)
        assertTrue(sql.contains("payload BYTEA"), sql)
        assertTrue(sql.contains("value DOUBLE PRECISION"), sql)
        assertTrue(sql.contains("registered_at TIMESTAMP(3)"), sql)
    }

    @Test
    fun `fails fast on mariadb only types for postgres`() {
        val migration = object : Migration() {
            override fun up() {
                create("postgres_invalid") {
                    tinyInteger("tiny_score")
                    enumValues("status", "draft", "published")
                }
            }

            override fun down() = Unit
        }

        val error = assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration, PostgreSqlDriver)
        }

        assertTrue(error.message.orEmpty().contains("PostgreSQL"))
    }
}
