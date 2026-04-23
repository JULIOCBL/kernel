package kernel.database.migrations

import kotlin.test.Test

class MigrationDatabaseObjectFeatureTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `generates schemas extensions and sequences`() {
        val migration = object : Migration() {
            override fun up() {
                createSchema("pos")
                renameSchema("pos", "point_of_sale")
                createExtension("pgcrypto", schema = "public", version = "1.3")
                createSequence(
                    "point_of_sale.ticket_numbers",
                    incrementBy = 1,
                    minValue = 1,
                    maxValue = 999999,
                    startWith = 1000,
                    cache = 10,
                    cycle = true,
                    ownedBy = "point_of_sale.orders.ticket_number"
                )
                renameSequence("point_of_sale.ticket_numbers", "point_of_sale.order_numbers")
            }

            override fun down() {
                dropSequence("point_of_sale.order_numbers", cascade = true)
                dropExtension("pgcrypto", cascade = true)
                dropSchema("point_of_sale", cascade = true)
            }
        }

        assertGeneratedStatements(
            listOf(
                "CREATE SCHEMA IF NOT EXISTS pos;",
                "ALTER SCHEMA pos RENAME TO point_of_sale;",
                "CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public VERSION '1.3';",
                "CREATE SEQUENCE IF NOT EXISTS point_of_sale.ticket_numbers INCREMENT BY 1 MINVALUE 1 MAXVALUE 999999 START WITH 1000 CACHE 10 CYCLE OWNED BY point_of_sale.orders.ticket_number;",
                "ALTER SEQUENCE point_of_sale.ticket_numbers RENAME TO point_of_sale.order_numbers;"
            ),
            generator.generateUpStatements(migration)
        )
        assertGeneratedStatements(
            listOf(
                "DROP SEQUENCE IF EXISTS point_of_sale.order_numbers CASCADE;",
                "DROP EXTENSION IF EXISTS pgcrypto CASCADE;",
                "DROP SCHEMA IF EXISTS point_of_sale CASCADE;"
            ),
            generator.generateDownStatements(migration)
        )
    }

    @Test
    fun `generates views materialized views comments functions and triggers`() {
        val migration = object : Migration() {
            override fun up() {
                createView("pos.open_orders", "SELECT * FROM pos.orders WHERE paid_at IS NULL")
                createMaterializedView(
                    "pos.daily_sales",
                    "SELECT current_date AS sold_on, 0::numeric AS total",
                    withData = false
                )
                refreshMaterializedView("pos.daily_sales", concurrently = true)
                commentOnTable("pos.orders", "POS orders")
                commentOnColumn("pos.orders", "total", "Generated order total")
                createFunction(
                    name = "pos.touch_updated_at",
                    returns = "trigger",
                    language = "plpgsql",
                    body = "BEGIN\n    NEW.updated_at = CURRENT_TIMESTAMP;\n    RETURN NEW;\nEND"
                )
                createTrigger(
                    name = "orders_touch_updated_at",
                    table = "pos.orders",
                    timing = "before",
                    "update",
                    function = "pos.touch_updated_at()"
                )
            }

            override fun down() {
                dropTrigger("orders_touch_updated_at", "pos.orders")
                dropFunction("pos.touch_updated_at()")
                commentOnColumn("pos.orders", "total", null)
                dropMaterializedView("pos.daily_sales")
                dropView("pos.open_orders")
            }
        }

        assertGeneratedStatements(
            listOf(
                """
                CREATE OR REPLACE VIEW pos.open_orders AS
                SELECT * FROM pos.orders WHERE paid_at IS NULL;
                """,
                """
                CREATE MATERIALIZED VIEW IF NOT EXISTS pos.daily_sales AS
                SELECT current_date AS sold_on, 0::numeric AS total WITH NO DATA;
                """,
                "REFRESH MATERIALIZED VIEW CONCURRENTLY pos.daily_sales WITH DATA;",
                "COMMENT ON TABLE pos.orders IS 'POS orders';",
                "COMMENT ON COLUMN pos.orders.total IS 'Generated order total';",
                """
                CREATE OR REPLACE FUNCTION pos.touch_updated_at()
                RETURNS trigger
                LANGUAGE plpgsql
                AS ${'$'}${'$'}
                BEGIN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                    RETURN NEW;
                END
                ${'$'}${'$'};
                """,
                """
                CREATE TRIGGER orders_touch_updated_at
                BEFORE UPDATE ON pos.orders
                FOR EACH ROW
                EXECUTE FUNCTION pos.touch_updated_at();
                """
            ),
            generator.generateUpStatements(migration)
        )
        assertGeneratedStatements(
            listOf(
                "DROP TRIGGER IF EXISTS orders_touch_updated_at ON pos.orders;",
                "DROP FUNCTION IF EXISTS pos.touch_updated_at();",
                "COMMENT ON COLUMN pos.orders.total IS NULL;",
                "DROP MATERIALIZED VIEW IF EXISTS pos.daily_sales;",
                "DROP VIEW IF EXISTS pos.open_orders;"
            ),
            generator.generateDownStatements(migration)
        )
    }
}
