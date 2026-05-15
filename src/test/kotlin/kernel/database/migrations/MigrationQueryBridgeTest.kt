package kernel.database.migrations

import kernel.database.DB
import kernel.database.orm.Model
import kernel.database.orm.ModelDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MigrationQueryBridgeTest {
    @Test
    fun `db table delete inside migration compiles to delete sql`() {
        val sql = DeleteLabUsersMigration().downSql()

        assertEquals(
            listOf("DELETE FROM lab_users WHERE id IN (1, 7, 15)"),
            sql
        )
    }

    @Test
    fun `model delete inside migration compiles to soft delete sql when configured`() {
        val sql = SoftDeleteLabUsersMigration().downSql()

        assertEquals(
            listOf("UPDATE lab_users SET deleted_at = CURRENT_TIMESTAMP WHERE id = 1 AND deleted_at IS NULL"),
            sql
        )
    }

    @Test
    fun `migration rejects explicit db connection when migration has no declared connection`() {
        val error = assertFailsWith<IllegalStateException> {
            ConnectionScopedDeleteMigration().downSql()
        }

        assertEquals(
            "DB.connection(\"logs\") dentro de migraciones requiere que la migracion declare `override val connectionName = \"logs\"`.",
            error.message
        )
    }

    @Test
    fun `migration allows explicit db connection when it matches migration connection`() {
        val sql = MatchingConnectionScopedDeleteMigration().downSql()

        assertEquals(
            listOf("DELETE FROM lab_users WHERE id = 1"),
            sql
        )
    }
}

private class DeleteLabUsersMigration : Migration() {
    override fun up() = Unit

    override fun down() {
        DB.table("lab_users")
            .whereIn("id", listOf(1, 7, 15))
            .delete()
    }
}

private class SoftDeleteLabUsersMigration : Migration() {
    override fun up() = Unit

    override fun down() {
        SoftDeleteLabUser.delete(1)
    }
}

private class ConnectionScopedDeleteMigration : Migration() {
    override fun up() = Unit

    override fun down() {
        DB.connection("logs")
            .table("lab_users")
            .where("id", "=", 1)
            .delete()
    }
}

private class MatchingConnectionScopedDeleteMigration : Migration() {
    override val connectionName: String = "logs"

    override fun up() = Unit

    override fun down() {
        DB.connection("logs")
            .table("lab_users")
            .where("id", "=", 1)
            .delete()
    }
}

private data class SoftDeleteLabUser(
    val id: Int,
    val email: String
) : Model() {
    override fun primaryKeyValue(): Any? = id

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "email" to email
        )
    }

    companion object : ModelDefinition<SoftDeleteLabUser>(
        tableName = "lab_users",
        mapper = { SoftDeleteLabUser(it.getInt("id"), it.getString("email")) },
        softDeleteColumn = "deleted_at"
    )
}
