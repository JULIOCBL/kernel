package kernel.database.pdo.drivers

object PostgreSqlDriver : DatabaseDriver {
    override val id: String = "pgsql"
    override val defaultJdbcDriverClass: String = "org.postgresql.Driver"
    override val supportsSchemaMigrations: Boolean = true
    override val supportsSchemaTransactions: Boolean = true

    override fun buildJdbcUrl(
        host: String,
        port: String,
        database: String
    ): String {
        return "jdbc:postgresql://$host:$port/$database"
    }
}
