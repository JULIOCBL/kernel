package kernel.database.migrations

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MigrationStubFactoryTest {
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-04-23T12:34:56Z"),
        ZoneOffset.UTC
    )

    private val factory = MigrationStubFactory(clock)

    @Test
    fun `generates blank stub with explicit name`() {
        val stub = factory.create(
            MigrationStubRequest(
                name = "Add audit columns",
                template = MigrationStubTemplate.BLANK
            )
        )

        assertEquals("M2026_04_23_123456_add_audit_columns", stub.className)
        assertEquals("M2026_04_23_123456_add_audit_columns.kt", stub.fileName)
        assertEquals(
            """
            package kernel.database.migrations

            /**
             * Migracion base lista para personalizar.
             */
            class M2026_04_23_123456_add_audit_columns : Migration() {
                /**
                 * Define las operaciones que aplican la migracion.
                 */
                override fun up() {

                }

                /**
                 * Define las operaciones que revierten la migracion.
                 */
                override fun down() {

                }
            }
            """.trimIndent(),
            stub.source
        )
    }

    @Test
    fun `generates create table stub from table name`() {
        val stub = factory.create(
            MigrationStubRequest(
                template = MigrationStubTemplate.CREATE_TABLE,
                tableName = "users"
            )
        )

        assertEquals("M2026_04_23_123456_create_users_table", stub.className)
        assertEquals(
            """
            package kernel.database.migrations

            /**
             * Migracion que crea la tabla `users`.
             */
            class M2026_04_23_123456_create_users_table : Migration() {
                /**
                 * Crea la tabla `users`.
                 */
                override fun up() {
                    create("users") {
                        id().primaryKey()
                        timestampsTz()
                    }
                }

                /**
                 * Elimina la tabla `users` si existe.
                 */
                override fun down() {
                    dropIfExists("users")
                }
            }
            """.trimIndent(),
            stub.source
        )
    }

    @Test
    fun `generates update table stub from explicit human name`() {
        val stub = factory.create(
            MigrationStubRequest(
                name = "Add profile photo to users",
                template = MigrationStubTemplate.UPDATE_TABLE,
                tableName = "Users"
            )
        )

        assertEquals("M2026_04_23_123456_add_profile_photo_to_users", stub.className)
        assertEquals(
            """
            package kernel.database.migrations

            /**
             * Migracion que modifica la tabla `users`.
             */
            class M2026_04_23_123456_add_profile_photo_to_users : Migration() {
                /**
                 * Aplica cambios sobre la tabla `users`.
                 */
                override fun up() {
                    table("users") {
                    }
                }

                /**
                 * Revierte manualmente los cambios aplicados en `up`.
                 */
                override fun down() {

                }
            }
            """.trimIndent(),
            stub.source
        )
    }

    @Test
    fun `requires explicit name for blank stub`() {
        assertFailsWith<IllegalArgumentException> {
            factory.create(
                MigrationStubRequest(
                    template = MigrationStubTemplate.BLANK,
                    tableName = "users"
                )
            )
        }
    }
}
