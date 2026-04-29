package kernel.database.pdo.drivers

object MariaDbDriver : DatabaseDriver {
    override val id: String = "mariadb"
    override val defaultJdbcDriverClass: String = "org.mariadb.jdbc.Driver"
    override val supportsSchemaMigrations: Boolean = true
    override val supportsSchemaTransactions: Boolean = false

    override fun buildJdbcUrl(
        host: String,
        port: String,
        database: String
    ): String {
        return "jdbc:mariadb://$host:$port/$database"
    }
}
