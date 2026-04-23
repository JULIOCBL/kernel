package kernel.database.migrations

import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationIntegrationTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `generates complete pos schema migration`() {
        val migration = object : Migration() {
            override fun up() {
                createSchema("pos")
                createExtension("pgcrypto")
                createEnum("payment_status", "pending", "paid", "cancelled")

                create("pos.customers") {
                    id().generatedUuid().primaryKey()
                    string("name").notNull()
                    string("email").unique()
                    timestampsTz()
                }

                create("pos.orders") {
                    id().generatedUuid().primaryKey()
                    foreignUuid("customer_id").notNull()
                    enumColumn("status", "payment_status").notNull().default("pending")
                    numeric("subtotal", 12, 2).notNull().default(0)
                    numeric("tax", 12, 2).notNull().default(0)
                    numeric("total", 12, 2).storedAs("subtotal + tax")
                    jsonb("metadata")
                    timestampTz("paid_at")
                    timestampsTz()
                    foreign("customer_id").references("id").on("pos.customers").cascadeOnDelete()
                    check("orders_subtotal_check", "subtotal >= 0")
                }

                table("pos.orders") {
                    index("customer_id", "created_at")
                    index("status", where = "deleted_at IS NULL")
                    gin("metadata", name = "orders_metadata_gin", where = "metadata IS NOT NULL")
                }

                createView(
                    "pos.paid_orders",
                    "SELECT * FROM pos.orders WHERE status = 'paid'"
                )
            }

            override fun down() {
                dropView("pos.paid_orders")
                dropIfExists("pos.orders")
                dropIfExists("pos.customers")
                dropEnum("payment_status")
                dropExtension("pgcrypto")
                dropSchema("pos")
            }
        }

        assertEquals(9, generator.generateUpStatements(migration).size)
        assertEquals(
            """
            DROP VIEW IF EXISTS pos.paid_orders;

            DROP TABLE IF EXISTS pos.orders;

            DROP TABLE IF EXISTS pos.customers;

            DROP TYPE IF EXISTS payment_status;

            DROP EXTENSION IF EXISTS pgcrypto;

            DROP SCHEMA IF EXISTS pos;
            """.trimIndent(),
            generator.generateDown(migration)
        )
    }
}
