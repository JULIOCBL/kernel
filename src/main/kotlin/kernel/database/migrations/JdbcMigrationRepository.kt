package kernel.database.migrations

import kernel.database.pdo.connections.ConnectionResolver
import java.sql.Connection

/**
 * Repositorio JDBC simple para guardar el historial de migraciones ejecutadas.
 *
 * Por ahora esta orientado al soporte oficial inicial del kernel (`pgsql`),
 * pero usa `DatabaseMetaData` para consultar existencia de tabla y mantiene la
 * interfaz suficientemente pequena para futuras implementaciones.
 */
class JdbcMigrationRepository(
    private val resolver: ConnectionResolver,
    private val table: String = DEFAULT_TABLE
) : MigrationRepository {
    private var sourceConnectionName: String? = null

    override fun getRan(): List<String> {
        return withConnection { connection ->
            connection.prepareStatement(
                "select migration from ${quotedTable()} order by batch asc, migration asc"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(result.getString("migration"))
                        }
                    }
                }
            }
        }
    }

    override fun getMigrationBatches(): Map<String, Int> {
        return withConnection { connection ->
            connection.prepareStatement(
                "select migration, batch from ${quotedTable()} order by batch asc, migration asc"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    buildMap {
                        while (result.next()) {
                            put(result.getString("migration"), result.getInt("batch"))
                        }
                    }
                }
            }
        }
    }

    override fun getMigrations(steps: Int): List<MigrationRecord> {
        require(steps > 0) {
            "El numero de pasos para rollback debe ser mayor a cero."
        }

        return withConnection { connection ->
            connection.prepareStatement(
                "select migration, batch from ${quotedTable()} " +
                    "where batch >= 1 order by batch desc, migration desc limit ?"
            ).use { statement ->
                statement.setInt(1, steps)
                statement.executeQuery().use { result ->
                    readRecords(result)
                }
            }
        }
    }

    override fun getLast(): List<MigrationRecord> {
        val lastBatch = getLastBatchNumber()

        if (lastBatch == 0) {
            return emptyList()
        }

        return withConnection { connection ->
            connection.prepareStatement(
                "select migration, batch from ${quotedTable()} " +
                    "where batch = ? order by migration desc"
            ).use { statement ->
                statement.setInt(1, lastBatch)
                statement.executeQuery().use { result ->
                    readRecords(result)
                }
            }
        }
    }

    override fun log(migration: String, batch: Int) {
        withConnection { connection ->
            connection.prepareStatement(
                "insert into ${quotedTable()} (migration, batch) values (?, ?)"
            ).use { statement ->
                statement.setString(1, migration)
                statement.setInt(2, batch)
                statement.executeUpdate()
            }
        }
    }

    override fun delete(record: MigrationRecord) {
        withConnection { connection ->
            connection.prepareStatement(
                "delete from ${quotedTable()} where migration = ?"
            ).use { statement ->
                statement.setString(1, record.migration)
                statement.executeUpdate()
            }
        }
    }

    override fun getNextBatchNumber(): Int {
        return getLastBatchNumber() + 1
    }

    override fun getLastBatchNumber(): Int {
        return withConnection { connection ->
            connection.prepareStatement(
                "select coalesce(max(batch), 0) as batch from ${quotedTable()}"
            ).use { statement ->
                statement.executeQuery().use { result ->
                    if (result.next()) result.getInt("batch") else 0
                }
            }
        }
    }

    override fun createRepository() {
        val connectionConfig = resolver.connectionConfig(sourceConnectionName)
        connectionConfig.requireSchemaMigrationSupport()

        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "create table if not exists ${quotedTable()} (" +
                        "id bigserial primary key, " +
                        "migration varchar(255) not null, " +
                        "batch integer not null)"
                )
            }
        }
    }

    override fun repositoryExists(): Boolean {
        val (schema, tableName) = tableParts()

        return withConnection { connection ->
            connection.metaData.getTables(null, schema, tableName, arrayOf("TABLE")).use { result ->
                result.next()
            }
        }
    }

    override fun deleteRepository() {
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute("drop table if exists ${quotedTable()}")
            }
        }
    }

    override fun setSource(name: String?) {
        sourceConnectionName = normalizedConnectionName(name)
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        return resolver.connection(sourceConnectionName).use(block)
    }

    private fun readRecords(result: java.sql.ResultSet): List<MigrationRecord> {
        return buildList {
            while (result.next()) {
                add(
                    MigrationRecord(
                        migration = result.getString("migration"),
                        batch = result.getInt("batch")
                    )
                )
            }
        }
    }

    private fun quotedTable(): String {
        val (schema, tableName) = tableParts()
        return buildList {
            schema?.let(::add)
            add(tableName)
        }.joinToString(".") { part -> "\"$part\"" }
    }

    private fun tableParts(): Pair<String?, String> {
        val parts = table.trim().split('.').filter(String::isNotBlank)
        require(parts.isNotEmpty()) {
            "El nombre de la tabla de migraciones no puede estar vacio."
        }

        val tableName = parts.last()
        val schema = parts.dropLast(1).takeIf(List<String>::isNotEmpty)?.joinToString(".")

        return schema to tableName
    }

    private fun normalizedConnectionName(name: String?): String? {
        return name?.trim()?.takeIf(String::isNotEmpty)
    }

    companion object {
        const val DEFAULT_TABLE: String = "migrations"
    }
}
