package kernel.database.pdo.drivers

/**
 * Registro simple de drivers soportados por el kernel.
 *
 * Por ahora solo existe PostgreSQL. Cuando agreguemos otro motor, debe entrar
 * aqui como un nuevo `DatabaseDriver` concreto.
 */
object DatabaseDrivers {
    private val supportedDrivers: Map<String, DatabaseDriver> = buildMap {
        register(PostgreSqlDriver)
    }

    fun resolve(value: String): DatabaseDriver {
        return supportedDrivers[value.trim().lowercase()]
            ?: throw IllegalArgumentException(
                "Motor de base de datos no soportado: `$value`. " +
                    "Soportados hoy: ${supportedIds().joinToString(", ")}."
            )
    }

    fun supportedIds(): List<String> = supportedDrivers.keys.sorted()

    private fun MutableMap<String, DatabaseDriver>.register(driver: DatabaseDriver) {
        this[driver.id] = driver
    }
}
