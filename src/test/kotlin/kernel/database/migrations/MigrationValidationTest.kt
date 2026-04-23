package kernel.database.migrations

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MigrationValidationTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `rejects postgres identifiers that are not snake case`() {
        val migration = object : Migration() {
            override fun up() {
                create("Users") {
                    id().primaryKey()
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects duplicated columns`() {
        val migration = object : Migration() {
            override fun up() {
                create("users") {
                    id().primaryKey()
                    uuid("id")
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects primary key columns that do not exist`() {
        val migration = object : Migration() {
            override fun up() {
                create("users") {
                    id()
                    primaryKey("missing_id")
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects invalid numeric scale`() {
        val migration = object : Migration() {
            override fun up() {
                create("products") {
                    id().primaryKey()
                    numeric("price", 8, 9)
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects add column blocks with more than one column`() {
        val migration = object : Migration() {
            override fun up() {
                addColumn("users") {
                    varchar("email", 255)
                    varchar("name", 255)
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects indexes without columns`() {
        val migration = object : Migration() {
            override fun up() {
                createIndex("users_email_index", "users")
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects enum without values`() {
        val migration = object : Migration() {
            override fun up() {
                createEnum("order_status")
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects duplicated enum values`() {
        val migration = object : Migration() {
            override fun up() {
                createEnum("order_status", "pending", "pending")
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects enum value with before and after`() {
        val migration = object : Migration() {
            override fun up() {
                addEnumValue("order_status", "refunded", before = "paid", after = "cancelled")
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects invalid temporal precision`() {
        val migration = object : Migration() {
            override fun up() {
                create("events") {
                    timestamp("created_at", precision = 7)
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects empty array element type`() {
        val migration = object : Migration() {
            override fun up() {
                create("events") {
                    array("tags", " ")
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects foreign key without referenced table`() {
        val migration = object : Migration() {
            override fun up() {
                create("orders") {
                    id().primaryKey()
                    foreignUuid("customer_id")
                    foreign("customer_id").references("id")
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalStateException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects default on generated column`() {
        val migration = object : Migration() {
            override fun up() {
                create("orders") {
                    numeric("subtotal", 12, 2)
                    numeric("tax", 12, 2)
                    numeric("total", 12, 2).storedAs("subtotal + tax").default(0)
                }
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }

    @Test
    fun `rejects trigger without events`() {
        val migration = object : Migration() {
            override fun up() {
                createTrigger(
                    name = "orders_touch_updated_at",
                    table = "orders",
                    timing = "before",
                    function = "touch_updated_at()"
                )
            }

            override fun down() = Unit
        }

        assertFailsWith<IllegalArgumentException> {
            generator.generateUp(migration)
        }
    }
}
