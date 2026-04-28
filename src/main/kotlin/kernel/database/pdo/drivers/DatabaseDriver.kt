package kernel.database.pdo.drivers

/**
 * Contrato base para un driver de base de datos conocido por el kernel.
 *
 * El driver sabe:
 *
 * - como identificarse en configuracion (`pgsql`, etc.);
 * - que clase JDBC usar por defecto;
 * - si el kernel soporta schema/migraciones para ese motor;
 * - como construir una URL JDBC basica desde host/port/database.
 */
interface DatabaseDriver {
    val id: String
    val defaultJdbcDriverClass: String
    val supportsSchemaMigrations: Boolean

    fun buildJdbcUrl(
        host: String,
        port: String,
        database: String
    ): String
}
