package kernel.database.migrations

import kotlin.test.Test

class MigrationAlterTableFeatureTest {
    private val generator = MigrationSqlGenerator()

    @Test
    fun `generates alter table column operations in declared order`() {
        val migration = object : Migration() {
            override fun up() {
                table("users") {
                    string("phone", 30)
                    renameColumn("name", "full_name")
                    changeColumnType("age", "BIGINT", usingExpression = "age::bigint")
                    setDefault("active", "TRUE")
                    dropDefault("active")
                    setNotNull("email")
                    dropNotNull("nickname")
                    dropColumn("legacy_code")
                }
            }

            override fun down() = Unit
        }

        assertGeneratedStatements(
            listOf(
                "ALTER TABLE users ADD COLUMN phone VARCHAR(30);",
                "ALTER TABLE users RENAME COLUMN name TO full_name;",
                "ALTER TABLE users ALTER COLUMN age TYPE BIGINT USING age::bigint;",
                "ALTER TABLE users ALTER COLUMN active SET DEFAULT TRUE;",
                "ALTER TABLE users ALTER COLUMN active DROP DEFAULT;",
                "ALTER TABLE users ALTER COLUMN email SET NOT NULL;",
                "ALTER TABLE users ALTER COLUMN nickname DROP NOT NULL;",
                "ALTER TABLE users DROP COLUMN IF EXISTS legacy_code;"
            ),
            generator.generateUpStatements(migration)
        )
    }

    @Test
    fun `generates direct alter table operations`() {
        val migration = object : Migration() {
            override fun up() {
                addColumn("users", ifNotExists = false) {
                    string("phone", 30)
                }
                renameColumn("users", "name", "full_name")
                alterColumnType("users", "age", "BIGINT")
                setColumnDefault("users", "active", "TRUE")
                dropColumnDefault("users", "active")
                setColumnNotNull("users", "email")
                dropColumnNotNull("users", "nickname")
                dropColumn("users", "legacy_code", ifExists = false, cascade = true)
            }

            override fun down() = Unit
        }

        assertGeneratedStatements(
            listOf(
                "ALTER TABLE users ADD COLUMN phone VARCHAR(30);",
                "ALTER TABLE users RENAME COLUMN name TO full_name;",
                "ALTER TABLE users ALTER COLUMN age TYPE BIGINT;",
                "ALTER TABLE users ALTER COLUMN active SET DEFAULT TRUE;",
                "ALTER TABLE users ALTER COLUMN active DROP DEFAULT;",
                "ALTER TABLE users ALTER COLUMN email SET NOT NULL;",
                "ALTER TABLE users ALTER COLUMN nickname DROP NOT NULL;",
                "ALTER TABLE users DROP COLUMN legacy_code CASCADE;"
            ),
            generator.generateUpStatements(migration)
        )
    }
}
