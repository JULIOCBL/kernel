package kernel.database.migrations

import kotlin.test.Test

class MigrationConstraintFeatureTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `generates create table constraints`() {
        val migration = object : Migration() {
            override fun up() {
                create("orders") {
                    id().primaryKey()
                    foreignUuid("customer_id").notNull()
                    numeric("total", 12, 2).notNull()
                    unique("store_id", "number")
                    check("orders_total_check", "total >= 0")
                    foreign("customer_id").references("id").on("customers").cascadeOnDelete()
                    exclude(
                        name = "orders_number_exclude",
                        using = "gist",
                        "number WITH ="
                    )
                }
            }

            override fun down() = Unit
        }

        assertGeneratedSql(
            """
            CREATE TABLE orders (
                id UUID NOT NULL,
                customer_id UUID NOT NULL,
                total NUMERIC(12, 2) NOT NULL,
                PRIMARY KEY (id),
                CONSTRAINT orders_store_id_number_unique UNIQUE (store_id, number),
                CONSTRAINT orders_total_check CHECK (total >= 0),
                CONSTRAINT orders_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
                CONSTRAINT orders_number_exclude EXCLUDE USING gist (number WITH =)
            );
            """,
            generator.generateUp(migration)
        )
    }

    @Test
    fun `generates alter table constraints and drops`() {
        val migration = object : Migration() {
            override fun up() {
                table("orders") {
                    foreign("customer_id").constrained("customers").restrictOnDelete().cascadeOnUpdate()
                    check("orders_total_check", "total >= 0")
                    uniqueConstraint("store_id", "number", name = "orders_store_number_unique")
                    renameConstraint("orders_total_check", "orders_total_positive_check")
                    dropConstraint("orders_legacy_check")
                }
                dropConstraint("orders", "orders_old_check", ifExists = false, cascade = true)
            }

            override fun down() = Unit
        }

        assertGeneratedStatements(
            listOf(
                "ALTER TABLE orders ADD CONSTRAINT orders_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT ON UPDATE CASCADE;",
                "ALTER TABLE orders ADD CONSTRAINT orders_total_check CHECK (total >= 0);",
                "ALTER TABLE orders ADD CONSTRAINT orders_store_number_unique UNIQUE (store_id, number);",
                "ALTER TABLE orders RENAME CONSTRAINT orders_total_check TO orders_total_positive_check;",
                "ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_legacy_check;",
                "ALTER TABLE orders DROP CONSTRAINT orders_old_check CASCADE;"
            ),
            generator.generateUpStatements(migration)
        )
    }
}
