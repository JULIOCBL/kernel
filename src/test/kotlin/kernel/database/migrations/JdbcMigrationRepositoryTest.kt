package kernel.database.migrations

import kernel.database.pdo.connections.ConnectionResolver
import kernel.database.pdo.connections.DatabaseConnectionConfig
import kernel.database.pdo.drivers.MariaDbDriver
import kernel.database.pdo.drivers.PostgreSqlDriver
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Statement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JdbcMigrationRepositoryTest {
    @Test
    fun `createRepository uses postgres ddl and identifier quoting`() {
        val resolver = RepositoryRecordingResolver(PostgreSqlDriver)
        val repository = JdbcMigrationRepository(resolver, "public.migrations")

        repository.createRepository()

        assertEquals(
            "create table if not exists \"public\".\"migrations\" (" +
                "id bigserial primary key, " +
                "migration varchar(255) not null, " +
                "batch integer not null)",
            resolver.handle.executedSql.single()
        )
    }

    @Test
    fun `createRepository uses mariadb ddl and identifier quoting`() {
        val resolver = RepositoryRecordingResolver(MariaDbDriver)
        val repository = JdbcMigrationRepository(resolver, "app.migrations")

        repository.createRepository()

        assertEquals(
            "create table if not exists `app`.`migrations` (" +
                "id bigint unsigned not null auto_increment primary key, " +
                "migration varchar(255) not null, " +
                "batch int not null)",
            resolver.handle.executedSql.single()
        )
    }

    @Test
    fun `repositoryExists uses catalog lookup for mariadb`() {
        val resolver = RepositoryRecordingResolver(MariaDbDriver).apply {
            handle.catalog = "pos_local"
            handle.tableExists = true
        }
        val repository = JdbcMigrationRepository(resolver, "migrations")

        val exists = repository.repositoryExists()

        assertTrue(exists)
        assertEquals("pos_local", resolver.handle.lastCatalog)
        assertEquals(null, resolver.handle.lastSchemaPattern)
        assertEquals("migrations", resolver.handle.lastTableNamePattern)
    }

    private class RepositoryRecordingResolver(
        private val driver: kernel.database.pdo.drivers.DatabaseDriver
    ) : ConnectionResolver {
        val handle = RepositoryConnectionHandle()

        override fun connection(name: String?): Connection = handle.open()

        override fun defaultConnectionName(): String = "main"

        override fun connectionConfig(name: String?): DatabaseConnectionConfig {
            return DatabaseConnectionConfig(
                name = name ?: "main",
                driver = driver,
                url = "jdbc:test"
            )
        }
    }

    private class RepositoryConnectionHandle {
        val executedSql = mutableListOf<String>()
        var tableExists: Boolean = false
        var catalog: String? = null
        var lastCatalog: String? = null
        var lastSchemaPattern: String? = null
        var lastTableNamePattern: String? = null

        fun open(): Connection {
            val statement = Proxy.newProxyInstance(
                Statement::class.java.classLoader,
                arrayOf(Statement::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "execute" -> {
                        executedSql += args?.firstOrNull() as String
                        true
                    }

                    "close" -> Unit
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as Statement

            val metaData = Proxy.newProxyInstance(
                DatabaseMetaData::class.java.classLoader,
                arrayOf(DatabaseMetaData::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "getTables" -> {
                        lastCatalog = args?.getOrNull(0) as String?
                        lastSchemaPattern = args?.getOrNull(1) as String?
                        lastTableNamePattern = args?.getOrNull(2) as String?
                        resultSetFor(tableExists)
                    }

                    else -> defaultValue(method.returnType)
                }
            } as DatabaseMetaData

            return Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "createStatement" -> statement
                    "getMetaData" -> metaData
                    "getCatalog" -> catalog
                    "close" -> Unit
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as Connection
        }

        private fun resultSetFor(exists: Boolean): ResultSet {
            var remaining = exists

            return Proxy.newProxyInstance(
                ResultSet::class.java.classLoader,
                arrayOf(ResultSet::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "next" -> {
                        if (remaining) {
                            remaining = false
                            true
                        } else {
                            false
                        }
                    }

                    "close" -> Unit
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }

        private fun defaultValue(type: Class<*>): Any? {
            return when {
                !type.isPrimitive -> null
                type == Boolean::class.javaPrimitiveType -> false
                type == Int::class.javaPrimitiveType -> 0
                type == Long::class.javaPrimitiveType -> 0L
                type == Double::class.javaPrimitiveType -> 0.0
                type == Float::class.javaPrimitiveType -> 0f
                type == Short::class.javaPrimitiveType -> 0.toShort()
                type == Byte::class.javaPrimitiveType -> 0.toByte()
                type == Char::class.javaPrimitiveType -> '\u0000'
                else -> null
            }
        }
    }
}
