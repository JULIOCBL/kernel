package kernel.database.migrations

import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationOrderingOptimizationTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `preserves declaration order for up and down statements`() {
        val migration = object : Migration() {
            override fun up() {
                createExtension("pgcrypto")
                createEnum("payment_status", "pending", "paid")
                create("customers") {
                    id().primaryKey()
                }
                create("orders") {
                    id().primaryKey()
                    foreignUuid("customer_id")
                    foreign("customer_id").constrained("customers")
                }
                table("orders") {
                    index("customer_id")
                }
                createView("paid_orders", "SELECT * FROM orders")
            }

            override fun down() {
                dropView("paid_orders")
                dropIfExists("orders")
                dropIfExists("customers")
                dropEnum("payment_status")
                dropExtension("pgcrypto")
            }
        }

        val upStatements = generator.generateUpStatements(migration)
        val downStatements = generator.generateDownStatements(migration)

        assertEquals(
            listOf(
                "CREATE EXTENSION IF NOT EXISTS pgcrypto;",
                "CREATE TYPE payment_status AS ENUM ('pending', 'paid');",
                """
                CREATE TABLE IF NOT EXISTS customers (
                    id UUID NOT NULL,
                    PRIMARY KEY (id)
                );
                """.trimIndent(),
                """
                CREATE TABLE IF NOT EXISTS orders (
                    id UUID NOT NULL,
                    customer_id UUID,
                    PRIMARY KEY (id),
                    CONSTRAINT orders_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES customers (id)
                );
                """.trimIndent(),
                "CREATE INDEX IF NOT EXISTS orders_customer_id_index ON orders (customer_id);",
                """
                CREATE OR REPLACE VIEW paid_orders AS
                SELECT * FROM orders;
                """.trimIndent()
            ),
            upStatements
        )
        assertEquals(
            listOf(
                "DROP VIEW IF EXISTS paid_orders;",
                "DROP TABLE IF EXISTS orders;",
                "DROP TABLE IF EXISTS customers;",
                "DROP TYPE IF EXISTS payment_status;",
                "DROP EXTENSION IF EXISTS pgcrypto;"
            ),
            downStatements
        )
    }

    @Test
    fun `keeps generated statements optimized and join format stable`() {
        val migration = object : Migration() {
            override fun up() {
                statement("CREATE EXTENSION IF NOT EXISTS pgcrypto;")
                create("users") {
                    id().primaryKey()
                }
                table("users") {
                    index("id")
                }
            }

            override fun down() = Unit
        }

        val statements = generator.generateUpStatements(migration)

        assertOptimizedSql(statements)
        assertEquals(statements.joinToString("\n\n"), generator.generateUp(migration))
    }
}
