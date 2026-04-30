package kernel.database

import kernel.config.MapConfigLoader
import kernel.database.pdo.drivers.MariaDbDriver
import kernel.database.pdo.drivers.PostgreSqlDriver
import kernel.foundation.Application
import kernel.foundation.ApplicationRuntime
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.util.Properties
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseManagerTest {
    private val fakeDriver = FakeDriver()

    @BeforeTest
    fun setUp() {
        ApplicationRuntime.resetForTests()
        DriverManager.registerDriver(fakeDriver)
        fakeDriver.reset()
    }

    @AfterTest
    fun tearDown() {
        DriverManager.deregisterDriver(fakeDriver)
        ApplicationRuntime.resetForTests()
    }

    @Test
    fun `resolves default and named connections from config`() {
        val application = buildApplication()

        application.loadConfig(
            MapConfigLoader(
                mapOf(
                    "database" to mapOf(
                        "default" to "primary",
                        "connections" to mapOf(
                            "primary" to mapOf(
                                "driver" to "pgsql",
                                "jdbcDriver" to FakeDriver::class.java.name,
                                "url" to "jdbc:kernel-test:primary",
                                "username" to "main_user",
                                "password" to "main_pass"
                            ),
                            "analytics" to mapOf(
                                "driver" to "pgsql",
                                "jdbcDriver" to FakeDriver::class.java.name,
                                "url" to "jdbc:kernel-test:analytics",
                                "username" to "analytics_user",
                                "password" to "analytics_pass",
                                "properties" to mapOf(
                                    "schema" to "reporting",
                                    "sslmode" to "disable"
                                )
                            )
                        )
                    )
                )
            )
        )

        val manager = DatabaseManager.from(application)

        assertEquals("primary", manager.defaultConnectionName())
        assertEquals(listOf("primary", "analytics"), manager.connectionNames())
        assertTrue(manager.hasConnection("analytics"))
        assertEquals("jdbc:kernel-test:primary", manager.connectionConfig().url)
        assertEquals("jdbc:kernel-test:analytics", manager.connectionConfig("analytics").url)
        assertEquals(PostgreSqlDriver, manager.connectionConfig().driver)
        assertEquals(PostgreSqlDriver, manager.connectionConfig("analytics").driver)
        assertEquals("reporting", manager.connectionConfig("analytics").properties["schema"])
    }

    @Test
    fun `connect opens configured jdbc connection`() {
        val application = buildApplication()
        application.loadConfig(
            "database",
            mapOf(
                "default" to "analytics",
                "connections" to mapOf(
                    "analytics" to mapOf(
                        "driver" to "pgsql",
                        "jdbcDriver" to FakeDriver::class.java.name,
                        "url" to "jdbc:kernel-test:analytics",
                        "username" to "analytics_user",
                        "password" to "analytics_pass",
                        "properties" to mapOf(
                            "schema" to "reporting"
                        )
                    )
                )
            )
        )

        val manager = DatabaseManager.from(application)
        manager.withConnection { connection ->
            assertTrue(connection.isWrapperFor(Connection::class.java))
        }
        manager.close()

        assertEquals("jdbc:kernel-test:analytics", fakeDriver.lastUrl)
        assertEquals("analytics_user", fakeDriver.lastProperties!!.getProperty("user"))
        assertEquals("analytics_pass", fakeDriver.lastProperties!!.getProperty("password"))
        assertEquals("reporting", fakeDriver.lastProperties!!.getProperty("schema"))
        assertTrue(fakeDriver.lastConnectionClosed)
    }

    @Test
    fun `manager is cached per application and pool config is materialized`() {
        val application = buildApplication()
        application.loadConfig(
            "database",
            mapOf(
                "default" to "main",
                "connections" to mapOf(
                    "main" to mapOf(
                        "driver" to "pgsql",
                        "jdbcDriver" to FakeDriver::class.java.name,
                        "url" to "jdbc:kernel-test:main",
                        "pool" to mapOf(
                            "enabled" to true,
                            "minimumIdle" to 2,
                            "maximumPoolSize" to 12
                        )
                    )
                )
            )
        )

        val first = DatabaseManager.from(application)
        val second = DatabaseManager.from(application)

        assertTrue(first === second)
        assertEquals(true, first.connectionConfig().pool.enabled)
        assertEquals(2, first.connectionConfig().pool.minimumIdle)
        assertEquals(12, first.connectionConfig().pool.maximumPoolSize)
    }

    @Test
    fun `global helper resolves manager from runtime`() {
        val application = Application.bootstrapRuntime(
            basePath = createTempDirectory("kernel-database-runtime-test").toAbsolutePath(),
            systemValues = emptyMap()
        )

        application.loadConfig(
            "database",
            mapOf(
                "default" to "primary",
                "connections" to mapOf(
                    "primary" to mapOf(
                        "driver" to "pgsql",
                        "url" to "jdbc:kernel-test:primary"
                    )
                )
            )
        )

        val manager = databaseManager()

        assertEquals("primary", manager.defaultConnectionName())
        assertEquals(listOf("primary"), manager.connectionNames())
    }

    @Test
    fun `fails when default connection is missing from config`() {
        val application = buildApplication()
        application.loadConfig(
            "database",
            mapOf(
                "connections" to mapOf(
                    "primary" to mapOf(
                        "driver" to "pgsql",
                        "url" to "jdbc:kernel-test:primary"
                    )
                )
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            DatabaseManager.from(application)
        }

        assertTrue(error.message!!.contains("database.default"))
    }

    @Test
    fun `fails when asking for an unknown connection`() {
        val application = buildApplication()
        application.loadConfig(
            "database",
            mapOf(
                "default" to "primary",
                "connections" to mapOf(
                    "primary" to mapOf(
                        "driver" to "pgsql",
                        "url" to "jdbc:kernel-test:primary"
                    )
                )
            )
        )

        val manager = DatabaseManager.from(application)

        val error = assertFailsWith<IllegalArgumentException> {
            manager.connectionConfig("reporting")
        }

        assertTrue(error.message!!.contains("reporting"))
    }

    @Test
    fun `fails when database engine is not supported`() {
        val application = buildApplication()
        application.loadConfig(
            "database",
            mapOf(
                "default" to "primary",
                "connections" to mapOf(
                    "primary" to mapOf(
                        "driver" to "mysql",
                        "url" to "jdbc:mysql://localhost:3306/app"
                    )
                )
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            DatabaseManager.from(application)
        }

        assertTrue(error.message!!.contains("Motor de base de datos no soportado"))
    }

    @Test
    fun `connection exposes whether schema migrations are supported`() {
        val application = buildApplication()
        application.loadConfig(
            "database",
            mapOf(
                "default" to "main",
                "connections" to mapOf(
                    "main" to mapOf(
                        "driver" to "pgsql",
                        "url" to "jdbc:kernel-test:main"
                    ),
                    "logs" to mapOf(
                        "driver" to "pgsql",
                        "url" to "jdbc:kernel-test:logs"
                    )
                )
            )
        )

        val manager = DatabaseManager.from(application)

        assertTrue(manager.connectionConfig("main").supportsSchemaMigrations())
        assertTrue(manager.connectionConfig("logs").supportsSchemaMigrations())
    }

    @Test
    fun `supports mariadb connections as a first class driver`() {
        val application = buildApplication()
        application.loadConfig(
            "database",
            mapOf(
                "default" to "main",
                "connections" to mapOf(
                    "main" to mapOf(
                        "driver" to "mariadb",
                        "url" to "jdbc:mariadb://127.0.0.1:3306/app",
                        "username" to "app_user",
                        "password" to "secret"
                    )
                )
            )
        )

        val manager = DatabaseManager.from(application)

        assertEquals(MariaDbDriver, manager.connectionConfig().driver)
        assertTrue(manager.connectionConfig().supportsSchemaMigrations())
        assertEquals(false, manager.connectionConfig().supportsSchemaTransactions())
    }

    private fun buildApplication(): Application {
        return Application.bootstrap(
            basePath = createTempDirectory("kernel-database-test").toAbsolutePath(),
            systemValues = emptyMap()
        )
    }
}

internal class FakeDriver : Driver {
    var lastUrl: String? = null
    var lastProperties: Properties? = null
    var lastConnectionClosed: Boolean = false

    fun reset() {
        lastUrl = null
        lastProperties = null
        lastConnectionClosed = false
    }

    override fun connect(url: String?, info: Properties?): Connection? {
        if (url == null || !acceptsURL(url)) {
            return null
        }

        lastUrl = url
        lastProperties = Properties().also { copy ->
            info?.forEach { key, value ->
                copy[key] = value
            }
        }

        val proxy = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, args ->
            when (method.name) {
                "close" -> {
                    lastConnectionClosed = true
                    null
                }

                "isClosed" -> lastConnectionClosed
                "isWrapperFor" -> {
                    val type = args?.firstOrNull() as? Class<*>
                    type?.isAssignableFrom(Connection::class.java) == true
                }

                "unwrap" -> {
                    val type = args?.firstOrNull() as? Class<*>
                    if (type?.isAssignableFrom(Connection::class.java) == true) {
                        null
                    } else {
                        throw IllegalArgumentException("Unsupported unwrap type: $type")
                    }
                }

                "toString" -> "FakeConnection($url)"
                else -> defaultValue(method.returnType)
            }
        }

        @Suppress("UNCHECKED_CAST")
        return proxy as Connection
    }

    override fun acceptsURL(url: String?): Boolean {
        return url?.startsWith("jdbc:kernel-test:") == true
    }

    override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> {
        return emptyArray()
    }

    override fun getMajorVersion(): Int = 1

    override fun getMinorVersion(): Int = 0

    override fun jdbcCompliant(): Boolean = false

    override fun getParentLogger(): java.util.logging.Logger {
        return java.util.logging.Logger.getGlobal()
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
            else -> null
        }
    }
}
