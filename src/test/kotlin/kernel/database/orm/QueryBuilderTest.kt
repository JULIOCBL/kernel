package kernel.database.orm

import kernel.database.DB
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QueryBuilderTest {
    @Test
    fun `build select sql includes joins where clauses order and pagination in canonical order`() {
        val sql = QueryBuilder(
            table = "tickets",
            rowMapper = { _ -> Unit }
        )
            .select("tickets.id", "users.name")
            .join("users", "users.id", "=", "tickets.user_id")
            .where("tickets.status", "=", "paid")
            .where("tickets.user_id", "=", 7)
            .orderBy("tickets.id", "desc")
            .limit(10)
            .offset(20)
            .buildSelectSql()

        assertEquals(
            "SELECT tickets.id, users.name FROM tickets JOIN users ON users.id = tickets.user_id WHERE tickets.status = ? AND tickets.user_id = ? ORDER BY tickets.id DESC LIMIT 10 OFFSET 20",
            sql
        )
    }

    @Test
    fun `query builder supports or where in null checks and reorder helpers`() {
        val sql = QueryBuilder(
            table = "tickets",
            rowMapper = { _ -> Unit }
        )
            .where("tickets.status", "=", "paid")
            .orWhere("tickets.status", "=", "pending")
            .whereIn("tickets.user_id", listOf(7, 8, 9))
            .whereNull("tickets.deleted_at")
            .latest("tickets.id")
            .reorder("tickets.created_at", "asc")
            .buildSelectSql()

        assertEquals(
            "SELECT * FROM tickets WHERE tickets.status = ? OR tickets.status = ? AND tickets.user_id IN (?, ?, ?) AND tickets.deleted_at IS NULL ORDER BY tickets.created_at ASC",
            sql
        )
    }

    @Test
    fun `query builder supports nested where groups`() {
        val sql = QueryBuilder(
            table = "tickets",
            rowMapper = { _ -> Unit }
        )
            .where {
                where("tickets.status", "=", "paid")
                orWhere("tickets.status", "=", "pending")
            }
            .where {
                whereIn("tickets.user_id", listOf(7, 8, 9))
                orWhereNull("tickets.deleted_at")
            }
            .orderByDesc("tickets.id")
            .buildSelectSql()

        assertEquals(
            "SELECT * FROM tickets WHERE (tickets.status = ? OR tickets.status = ?) AND (tickets.user_id IN (?, ?, ?) OR tickets.deleted_at IS NULL) ORDER BY tickets.id DESC",
            sql
        )
    }

    @Test
    fun `query builder supports leading where group followed by or where group`() {
        val sql = QueryBuilder(
            table = "tickets",
            rowMapper = { _ -> Unit }
        )
            .where {
                where("tickets.status", "=", "paid")
                whereNotNull("tickets.approved_at")
            }
            .orWhere {
                where("tickets.status", "=", "pending")
                whereNull("tickets.deleted_at")
            }
            .buildSelectSql()

        assertEquals(
            "SELECT * FROM tickets WHERE (tickets.status = ? AND tickets.approved_at IS NOT NULL) OR (tickets.status = ? AND tickets.deleted_at IS NULL)",
            sql
        )
    }

    @Test
    fun `query builder rejects unsafe identifiers`() {
        val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
            QueryBuilder(
                table = "tickets; DROP TABLE users",
                rowMapper = { _ -> Unit }
            ).buildSelectSql()
        }

        assertEquals(
            "Identificador SQL inválido: `tickets; DROP TABLE users`.",
            error.message
        )
    }

    @Test
    fun `query builder rejects unsafe operators`() {
        val error = kotlin.test.assertFailsWith<IllegalArgumentException> {
            QueryBuilder(
                table = "tickets",
                rowMapper = { _ -> Unit }
            ).where("id", "OR 1=1", 1)
        }

        assertEquals(
            "Operador SQL no permitido: `OR 1=1`.",
            error.message
        )
    }

    @Test
    fun `query builder builds postgres upsert sql`() {
        val sql = QueryBuilder(
            table = "orders_statuses",
            rowMapper = { _ -> Unit }
        ).buildUpsertSql(
            driverId = "pgsql",
            columns = listOf("id", "name_en", "name_es", "name_fr", "name_pt"),
            uniqueBy = listOf("id"),
            updateColumns = listOf("name_es", "name_en", "name_fr", "name_pt"),
            recordCount = 2
        )

        assertEquals(
            "INSERT INTO orders_statuses (id, name_en, name_es, name_fr, name_pt) VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET name_es = EXCLUDED.name_es, name_en = EXCLUDED.name_en, name_fr = EXCLUDED.name_fr, name_pt = EXCLUDED.name_pt",
            sql
        )
    }

    @Test
    fun `query builder builds mariadb upsert sql`() {
        val sql = QueryBuilder(
            table = "orders_statuses",
            rowMapper = { _ -> Unit }
        ).buildUpsertSql(
            driverId = "mariadb",
            columns = listOf("id", "name_en", "name_es", "name_fr", "name_pt"),
            uniqueBy = listOf("id"),
            updateColumns = listOf("name_es", "name_en", "name_fr", "name_pt"),
            recordCount = 2
        )

        assertEquals(
            "INSERT INTO orders_statuses (id, name_en, name_es, name_fr, name_pt) VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name_es = VALUES(name_es), name_en = VALUES(name_en), name_fr = VALUES(name_fr), name_pt = VALUES(name_pt)",
            sql
        )
    }

    @Test
    fun `query builder can delete rows using where clauses`() = runBlocking {
        val recorder = DeleteRecorder()
        DB.connectionProviderOverride = { recorder.connection }

        try {
            val affectedRows = QueryBuilder(
                table = "lab_users",
                rowMapper = { _ -> Unit }
            )
                .where("status", "=", "inactive")
                .delete()

            assertEquals(3, affectedRows)
            assertEquals("DELETE FROM lab_users WHERE status = ?", recorder.lastSql)
            assertEquals(listOf<Any?>("inactive"), recorder.boundValues)
        } finally {
            DB.connectionProviderOverride = null
        }
    }

    @Test
    fun `query builder can delete rows using where in`() = runBlocking {
        val recorder = DeleteRecorder()
        DB.connectionProviderOverride = { recorder.connection }

        try {
            val affectedRows = QueryBuilder(
                table = "lab_users",
                rowMapper = { _ -> Unit }
            )
                .whereIn("id", listOf(1, 7, 15))
                .delete()

            assertEquals(3, affectedRows)
            assertEquals("DELETE FROM lab_users WHERE id IN (?, ?, ?)", recorder.lastSql)
            assertEquals(listOf<Any?>(1, 7, 15), recorder.boundValues)
        } finally {
            DB.connectionProviderOverride = null
        }
    }

    @Test
    fun `query builder turns delete into soft delete when configured`() = runBlocking {
        val recorder = DeleteRecorder()
        DB.connectionProviderOverride = { recorder.connection }

        try {
            val affectedRows = QueryBuilder(
                table = "lab_users",
                rowMapper = { _ -> Unit },
                softDeleteColumn = "deleted_at"
            )
                .whereIn("id", listOf(1, 7, 15))
                .delete()

            assertEquals(3, affectedRows)
            assertEquals(
                "UPDATE lab_users SET deleted_at = CURRENT_TIMESTAMP WHERE id IN (?, ?, ?) AND deleted_at IS NULL",
                recorder.lastSql
            )
            assertEquals(listOf<Any?>(1, 7, 15), recorder.boundValues)
        } finally {
            DB.connectionProviderOverride = null
        }
    }

    @Test
    fun `query builder rejects delete without where clause`() = runBlocking {
        val error = assertFailsWith<IllegalArgumentException> {
            QueryBuilder(
                table = "lab_users",
                rowMapper = { _ -> Unit }
            ).delete()
        }

        assertEquals(
            "Por seguridad, `delete()` requiere al menos una clausula `where` en `lab_users`.",
            error.message
        )
    }

    @Test
    fun `query builder applies soft delete scope by default and can expose trashed rows`() {
        val builder = QueryBuilder(
            table = "lab_users",
            rowMapper = { _ -> Unit },
            softDeleteColumn = "deleted_at"
        )

        assertEquals(
            "SELECT * FROM lab_users WHERE deleted_at IS NULL",
            builder.buildSelectSql()
        )
        assertEquals(
            "SELECT * FROM lab_users",
            builder.withTrashed().buildSelectSql()
        )
        assertEquals(
            "SELECT * FROM lab_users WHERE deleted_at IS NOT NULL",
            builder.onlyTrashed().buildSelectSql()
        )
    }
}

private class DeleteRecorder {
    var lastSql: String? = null
    val boundValues = mutableListOf<Any?>()

    val connection: Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java)
    ) { _, method, args ->
        when (method.name) {
            "prepareStatement" -> preparedStatement(args?.firstOrNull() as String)
            "close" -> null
            "isClosed" -> false
            "unwrap" -> null
            "isWrapperFor" -> false
            "toString" -> "DeleteRecorderConnection"
            else -> defaultValue(method.returnType)
        }
    } as Connection

    private fun preparedStatement(sql: String): PreparedStatement {
        lastSql = sql
        boundValues.clear()

        return Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java)
        ) { _, method, args ->
            when (method.name) {
                "setObject" -> {
                    val index = (args?.get(0) as Int) - 1
                    while (boundValues.size <= index) {
                        boundValues += null
                    }
                    boundValues[index] = args[1]
                    null
                }

                "executeUpdate" -> 3
                "close" -> null
                "unwrap" -> null
                "isWrapperFor" -> false
                "toString" -> "DeleteRecorderPreparedStatement"
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when {
            type == Boolean::class.javaPrimitiveType -> false
            type == Int::class.javaPrimitiveType -> 0
            type == Long::class.javaPrimitiveType -> 0L
            type == Short::class.javaPrimitiveType -> 0.toShort()
            type == Byte::class.javaPrimitiveType -> 0.toByte()
            type == Double::class.javaPrimitiveType -> 0.0
            type == Float::class.javaPrimitiveType -> 0f
            type == Char::class.javaPrimitiveType -> '\u0000'
            type == Void.TYPE -> Unit
            else -> null
        }
    }
}
