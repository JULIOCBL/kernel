package kernel.database.migrations.repository

import java.sql.Connection

/**
 * Diferencias SQL y de metadata para el repository de migraciones por motor.
 */
internal interface MigrationRepositoryDialect {
    fun createRepositorySql(quotedTable: String): String

    fun repositoryExists(connection: Connection, schema: String?, tableName: String): Boolean

    fun quoteIdentifier(part: String): String
}

internal object MigrationRepositoryDialects {
    fun forDriver(driverId: String): MigrationRepositoryDialect {
        return when (driverId) {
            "mariadb" -> MariaDbMigrationRepositoryDialect
            "pgsql" -> PostgresMigrationRepositoryDialect
            else -> throw IllegalArgumentException(
                "No existe un dialecto de repository para el driver `$driverId`."
            )
        }
    }
}

private object PostgresMigrationRepositoryDialect : MigrationRepositoryDialect {
    override fun createRepositorySql(quotedTable: String): String {
        return "create table if not exists $quotedTable (" +
            "id bigserial primary key, " +
            "migration varchar(255) not null, " +
            "batch integer not null)"
    }

    override fun repositoryExists(connection: Connection, schema: String?, tableName: String): Boolean {
        return connection.metaData.getTables(null, schema, tableName, arrayOf("TABLE")).use { result ->
            result.next()
        }
    }

    override fun quoteIdentifier(part: String): String {
        return "\"$part\""
    }
}

private object MariaDbMigrationRepositoryDialect : MigrationRepositoryDialect {
    override fun createRepositorySql(quotedTable: String): String {
        return "create table if not exists $quotedTable (" +
            "id bigint unsigned not null auto_increment primary key, " +
            "migration varchar(255) not null, " +
            "batch int not null)"
    }

    override fun repositoryExists(connection: Connection, schema: String?, tableName: String): Boolean {
        val catalog = schema ?: connection.catalog
        return connection.metaData.getTables(catalog, null, tableName, arrayOf("TABLE")).use { result ->
            result.next()
        }
    }

    override fun quoteIdentifier(part: String): String {
        return "`$part`"
    }
}
