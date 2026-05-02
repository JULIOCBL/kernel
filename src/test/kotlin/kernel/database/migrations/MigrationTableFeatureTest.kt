package kernel.database.migrations

import kotlin.test.Test

class MigrationTableFeatureTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `creates drops and renames tables using laravel style api`() {
        val migration = object : Migration() {
            override fun up() {
                create("users") {
                    id().primaryKey()
                    string("email").notNull().unique()
                    timestampsTz()
                }
                rename("users", "customers")
            }

            override fun down() {
                rename("customers", "users")
                dropIfExists("users")
            }
        }

        assertGeneratedStatements(
            listOf(
                """
                CREATE TABLE users (
                    id UUID NOT NULL,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    created_at TIMESTAMPTZ,
                    updated_at TIMESTAMPTZ,
                    PRIMARY KEY (id)
                );
                """,
                "ALTER TABLE users RENAME TO customers;"
            ),
            generator.generateUpStatements(migration)
        )
        assertGeneratedStatements(
            listOf(
                "ALTER TABLE customers RENAME TO users;",
                "DROP TABLE IF EXISTS users;"
            ),
            generator.generateDownStatements(migration)
        )
    }

    @Test
    fun `supports descriptive table api aliases`() {
        val migration = object : Migration() {
            override fun up() {
                createTable("products", ifNotExists = false) {
                    id().primaryKey()
                }
                renameTable("products", "items")
            }

            override fun down() {
                dropTable("items", ifExists = false)
            }
        }

        assertGeneratedStatements(
            listOf(
                """
                CREATE TABLE products (
                    id UUID NOT NULL,
                    PRIMARY KEY (id)
                );
                """,
                "ALTER TABLE products RENAME TO items;"
            ),
            generator.generateUpStatements(migration)
        )
        assertGeneratedStatements(
            listOf("DROP TABLE items;"),
            generator.generateDownStatements(migration)
        )
    }
}
