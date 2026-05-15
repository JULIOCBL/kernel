package kernel.database.orm

import kernel.database.DB
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelConventionTest {
    @Test
    fun `query builder infers plural snake case table names from singular model names`() {
        assertEquals(
            "SELECT * FROM blog_posts",
            BlogPost.query().buildSelectSql()
        )

        assertEquals(
            "SELECT * FROM categories",
            Category.query().buildSelectSql()
        )
    }

    @Test
    fun `model definition can still override table name when convention is not desired`() {
        assertEquals(
            "SELECT * FROM legacy_people",
            LegacyPerson.query().buildSelectSql()
        )
    }

    @Test
    fun `model definition can expose soft delete aware query helpers`() {
        assertEquals(
            "SELECT * FROM lab_users WHERE deleted_at IS NULL",
            LabUser.query().buildSelectSql()
        )

        assertEquals(
            "SELECT * FROM lab_users WHERE id IN (?, ?, ?) AND deleted_at IS NULL",
            LabUser.whereIn("id", listOf(1, 7, 15)).buildSelectSql()
        )

        assertEquals(
            "SELECT * FROM lab_users",
            LabUser.withTrashed().buildSelectSql()
        )
    }

    @Test
    fun `model definition can delete by primary key shortcut`() = runBlocking {
        val recorder = ModelDeleteRecorder()
        DB.connectionProviderOverride = { recorder.connection }

        try {
            val affectedRows = LabUser.delete(1)

            assertEquals(1, affectedRows)
            assertEquals(
                "UPDATE lab_users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ? AND deleted_at IS NULL",
                recorder.lastSql
            )
            assertEquals(listOf<Any?>(1), recorder.boundValues)
        } finally {
            DB.connectionProviderOverride = null
        }
    }

    @Test
    fun `model definition delete shortcut respects custom primary key`() = runBlocking {
        val recorder = ModelDeleteRecorder()
        DB.connectionProviderOverride = { recorder.connection }

        try {
            val affectedRows = LegacyTicket.delete("A-100")

            assertEquals(1, affectedRows)
            assertEquals(
                "DELETE FROM legacy_tickets WHERE ticket_code = ?",
                recorder.lastSql
            )
            assertEquals(listOf<Any?>("A-100"), recorder.boundValues)
        } finally {
            DB.connectionProviderOverride = null
        }
    }
}

private data class BlogPost(
    val id: Int,
    val title: String
) : Model() {
    override fun primaryKeyValue(): Any? = id

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "title" to title
        )
    }

    companion object : ModelDefinition<BlogPost>(
        mapper = { BlogPost(it.getInt("id"), it.getString("title")) }
    )
}

private data class Category(
    val id: Int,
    val name: String
) : Model() {
    override fun primaryKeyValue(): Any? = id

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "name" to name
        )
    }

    companion object : ModelDefinition<Category>(
        mapper = { Category(it.getInt("id"), it.getString("name")) }
    )
}

private data class LegacyPerson(
    val id: Int,
    val displayName: String
) : Model() {
    override fun primaryKeyValue(): Any? = id

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "display_name" to displayName
        )
    }

    companion object : ModelDefinition<LegacyPerson>(
        tableName = "legacy_people",
        mapper = { LegacyPerson(it.getInt("id"), it.getString("display_name")) }
    )
}

private data class LabUser(
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

    companion object : ModelDefinition<LabUser>(
        tableName = "lab_users",
        mapper = { LabUser(it.getInt("id"), it.getString("email")) },
        softDeleteColumn = "deleted_at"
    )
}

private data class LegacyTicket(
    val ticketCode: String,
    val description: String
) : Model() {
    override fun primaryKeyValue(): Any? = ticketCode

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "ticket_code" to ticketCode,
            "description" to description
        )
    }

    companion object : ModelDefinition<LegacyTicket>(
        tableName = "legacy_tickets",
        mapper = { LegacyTicket(it.getString("ticket_code"), it.getString("description")) },
        primaryKey = "ticket_code"
    )
}

private class ModelDeleteRecorder {
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
            "toString" -> "ModelDeleteRecorderConnection"
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

                "executeUpdate" -> 1
                "close" -> null
                "unwrap" -> null
                "isWrapperFor" -> false
                "toString" -> "ModelDeleteRecorderPreparedStatement"
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
