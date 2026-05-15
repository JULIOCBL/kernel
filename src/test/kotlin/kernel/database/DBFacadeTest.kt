package kernel.database

import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import kotlin.test.Test
import kotlin.test.assertEquals

class DBFacadeTest {
    @Test
    fun `db table uses default connection when none is provided`() = runBlocking {
        val recorder = ConnectionNameRecorder()
        DB.connectionProviderOverride = { name ->
            recorder.requestedConnectionNames += name
            recorder.connection
        }
        DB.defaultConnectionNameOverride = { "main" }

        try {
            val affectedRows = DB.table("lab_users")
                .where("id", "=", 1)
                .delete()

            assertEquals(1, affectedRows)
            assertEquals(listOf<String?>("main"), recorder.requestedConnectionNames)
            assertEquals("DELETE FROM lab_users WHERE id = ?", recorder.lastSql)
            assertEquals(listOf<Any?>(1), recorder.boundValues)
        } finally {
            DB.connectionProviderOverride = null
            DB.defaultConnectionNameOverride = null
        }
    }

    @Test
    fun `db connection facade uses the requested connection name`() = runBlocking {
        val recorder = ConnectionNameRecorder()
        DB.connectionProviderOverride = { name ->
            recorder.requestedConnectionNames += name
            recorder.connection
        }

        try {
            val affectedRows = DB.connection("mysql2")
                .table("lab_users")
                .whereIn("id", listOf(1, 7, 15))
                .delete()

            assertEquals(1, affectedRows)
            assertEquals(listOf<String?>("mysql2"), recorder.requestedConnectionNames)
            assertEquals("DELETE FROM lab_users WHERE id IN (?, ?, ?)", recorder.lastSql)
            assertEquals(listOf<Any?>(1, 7, 15), recorder.boundValues)
        } finally {
            DB.connectionProviderOverride = null
        }
    }
}

private class ConnectionNameRecorder {
    val requestedConnectionNames = mutableListOf<String?>()
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
            "toString" -> "ConnectionNameRecorderConnection"
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
                "toString" -> "ConnectionNameRecorderPreparedStatement"
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
