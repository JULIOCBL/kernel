package kernel.database.migrations

import kotlin.test.Test

class MigrationIndexFeatureTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `generates index variants`() {
        val migration = object : Migration() {
            override fun up() {
                table("orders") {
                    index("customer_id", "created_at")
                    unique("store_id", "number")
                    gin("metadata", name = "orders_metadata_gin", where = "metadata IS NOT NULL")
                    gist("during", name = "orders_during_gist")
                    brin("created_at", name = "orders_created_at_brin", concurrently = true)
                    indexExpression(
                        name = "orders_lower_code_index",
                        expression = "lower(code)",
                        include = listOf("id"),
                        where = "deleted_at IS NULL"
                    )
                }
                createIndex(
                    "orders_status_index",
                    "orders",
                    "status",
                    using = "btree",
                    include = listOf("id"),
                    where = "deleted_at IS NULL"
                )
            }

            override fun down() {
                table("orders") {
                    dropIndex("orders_metadata_gin")
                }
                dropIndex("orders_status_index", concurrently = true)
            }
        }

        assertGeneratedStatements(
            listOf(
                "CREATE INDEX orders_customer_id_created_at_index ON orders (customer_id, created_at);",
                "CREATE UNIQUE INDEX orders_store_id_number_unique ON orders (store_id, number);",
                "CREATE INDEX orders_metadata_gin ON orders USING gin (metadata) WHERE metadata IS NOT NULL;",
                "CREATE INDEX orders_during_gist ON orders USING gist (during);",
                "CREATE INDEX CONCURRENTLY orders_created_at_brin ON orders USING brin (created_at);",
                "CREATE INDEX orders_lower_code_index ON orders (lower(code)) INCLUDE (id) WHERE deleted_at IS NULL;",
                "CREATE INDEX orders_status_index ON orders USING btree (status) INCLUDE (id) WHERE deleted_at IS NULL;"
            ),
            generator.generateUpStatements(migration)
        )
        assertGeneratedStatements(
            listOf(
                "DROP INDEX IF EXISTS orders_metadata_gin /* table: orders */;",
                "DROP INDEX CONCURRENTLY IF EXISTS orders_status_index;"
            ),
            generator.generateDownStatements(migration)
        )
    }
}
