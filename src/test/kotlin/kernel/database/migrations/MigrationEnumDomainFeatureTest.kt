package kernel.database.migrations

import kotlin.test.Test

class MigrationEnumDomainFeatureTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `generates enum lifecycle and enum columns`() {
        val migration = object : Migration() {
            override fun up() {
                createEnum("order_status", "pending", "paid", "cancelled")
                create("orders") {
                    id().primaryKey()
                    enumColumn("status", "order_status").notNull().default("pending")
                }
                addEnumValue("order_status", "refunded", after = "paid")
                renameEnumValue("order_status", "cancelled", "voided")
                renameEnum("order_status", "payment_status")
            }

            override fun down() {
                dropIfExists("orders")
                dropEnum("payment_status")
            }
        }

        assertGeneratedStatements(
            listOf(
                "CREATE TYPE order_status AS ENUM ('pending', 'paid', 'cancelled');",
                """
                CREATE TABLE orders (
                    id UUID NOT NULL,
                    status order_status NOT NULL DEFAULT 'pending',
                    PRIMARY KEY (id)
                );
                """,
                "ALTER TYPE order_status ADD VALUE 'refunded' AFTER 'paid';",
                "ALTER TYPE order_status RENAME VALUE 'cancelled' TO 'voided';",
                "ALTER TYPE order_status RENAME TO payment_status;"
            ),
            generator.generateUpStatements(migration)
        )
        assertGeneratedStatements(
            listOf(
                "DROP TABLE IF EXISTS orders;",
                "DROP TYPE IF EXISTS payment_status;"
            ),
            generator.generateDownStatements(migration)
        )
    }

    @Test
    fun `generates domains`() {
        val migration = object : Migration() {
            override fun up() {
                createDomain(
                    name = "positive_money",
                    type = "NUMERIC(12, 2)",
                    notNull = true,
                    defaultExpression = "0",
                    checkExpression = "VALUE >= 0"
                )
            }

            override fun down() {
                dropDomain("positive_money", cascade = true)
            }
        }

        assertGeneratedStatements(
            listOf("CREATE DOMAIN positive_money AS NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (VALUE >= 0);"),
            generator.generateUpStatements(migration)
        )
        assertGeneratedStatements(
            listOf("DROP DOMAIN IF EXISTS positive_money CASCADE;"),
            generator.generateDownStatements(migration)
        )
    }
}
