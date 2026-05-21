package kernel.command.commands

import kernel.database.pdo.connections.DatabaseManager
import kernel.database.pdo.drivers.DatabaseDrivers
import kernel.foundation.Application
import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult
import kernel.database.migrations.MigrationStubFactory
import kernel.database.migrations.MigrationStubRequest
import kernel.database.migrations.MigrationStubTemplate
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Genera un archivo de migracion Kotlin dentro de la carpeta de migraciones.
 */
data class MigrationGenerationConfig(
    val packageName: String = "kernel.database.migrations",
    val directory: String = "src/main/kotlin/kernel/database/migrations"
)

class MakeMigrationCommand(
    private val application: Application? = null,
    private val defaults: MigrationGenerationConfig = MigrationGenerationConfig(),
    private val stubFactory: MigrationStubFactory = MigrationStubFactory()
) : Command {
    override val name: String = "make:migration"
    override val description: String =
        "Genera un archivo de migracion Kotlin. Ejemplo: ./kernel make:migration create_users_table --create=users"
    override val usage: String =
        "make:migration <name> [--create=table] [--table=table] [--drop=table] [--database=conn] [--driver=driver]"

    override fun execute(input: CommandInput): CommandResult {
        val migrationName = input.argument(0)
            ?: throw IllegalArgumentException(
                "Debes indicar el nombre de la migracion. Ejemplo: make:migration create_users_table"
            )

        val (template, tableName) = resolveTemplate(input, migrationName)
        val connectionName = resolveTargetConnectionName(input)
        val packageName = resolvePackageName()
        val stub = stubFactory.create(
            MigrationStubRequest(
                template = template,
                name = migrationName,
                tableName = tableName,
                packageName = packageName
            )
        )

        val migrationsDirectory = resolveMigrationsDirectory(input.workingDirectory)
        Files.createDirectories(migrationsDirectory)

        val targetFile = migrationsDirectory.resolve(stub.fileName)

        require(!Files.exists(targetFile)) {
            "La migracion `${stub.fileName}` ya existe en $migrationsDirectory."
        }

        Files.writeString(
            targetFile,
            adaptSource(
                source = stub.source,
                packageName = packageName,
                connectionName = connectionName
            ),
            StandardCharsets.UTF_8
        )

        return CommandResult(
            exitCode = 0,
            message = buildString {
                appendLine("Migracion creada: $targetFile")
                connectionName?.let { value ->
                    append("Conexion fijada en la clase: `$value`.")
                }
            }.trimEnd()
        )
    }

    private fun resolveTemplate(
        input: CommandInput,
        migrationName: String
    ): Pair<MigrationStubTemplate, String?> {
        input.option("create")?.let { tableName ->
            return MigrationStubTemplate.CREATE_TABLE to tableName
        }

        input.option("table")?.let { tableName ->
            return MigrationStubTemplate.UPDATE_TABLE to tableName
        }

        input.option("drop")?.let { tableName ->
            return MigrationStubTemplate.DROP_TABLE to tableName
        }

        inferTableNameFromMigrationName(migrationName)?.let { tableName ->
            return MigrationStubTemplate.CREATE_TABLE to tableName
        }

        return MigrationStubTemplate.BLANK to null
    }

    private fun inferTableNameFromMigrationName(migrationName: String): String? {
        val match = CREATE_TABLE_PATTERN.matchEntire(migrationName.trim().lowercase()) ?: return null
        return match.groupValues[1]
    }

    private fun resolveTargetConnectionName(input: CommandInput): String? {
        input.option("database")?.let { rawConnectionName ->
            val connectionName = rawConnectionName.trim()
            require(connectionName.isNotEmpty()) {
                "La opcion `--database` no puede estar vacia."
            }

            configuredDatabaseManager()?.let { database ->
                database.connectionConfig(connectionName)
            }

            return connectionName
        }

        input.option("driver")?.let { rawDriver ->
            val driverId = DatabaseDrivers.resolve(rawDriver).id
            val database = configuredDatabaseManager()

            if (database != null) {
                if (database.hasConnection(driverId)) {
                    return driverId
                }

                val matchingConnections = database.connectionNames()
                    .filter { connectionName ->
                        database.connectionConfig(connectionName).driver.id == driverId
                    }

                require(matchingConnections.size <= 1) {
                    "El driver `$driverId` coincide con multiples conexiones (${matchingConnections.joinToString(", ")}). " +
                        "Usa `--database=<conexion>` para indicar una conexion concreta."
                }

                return matchingConnections.singleOrNull()
                    ?: throw IllegalArgumentException(
                        "No existe una conexion configurada con driver `$driverId`."
                    )
            }

            return driverId
        }

        return null
    }

    private fun configuredDatabaseManager(): DatabaseManager? {
        val app = application ?: return null
        if (!app.config.has("database.connections") && !app.config.has("database.default")) {
            return null
        }

        return runCatching { DatabaseManager.from(app) }.getOrNull()
    }

    private fun resolvePackageName(): String {
        val configured = application?.config
            ?.string("app.generators.migrations.package", defaults.packageName)
            .orEmpty()
            .trim()

        return configured.ifBlank { defaults.packageName }
    }

    private fun resolveMigrationsDirectory(workingDirectory: Path): Path {
        val configured = application?.config
            ?.string("app.generators.migrations.directory", defaults.directory)
            .orEmpty()
            .trim()
            .ifBlank { defaults.directory }

        return workingDirectory.resolve(configured)
    }

    private fun adaptSource(
        source: String,
        packageName: String,
        connectionName: String?
    ): String {
        val packageDeclaration = "package $packageName"
        val importLine = "import kernel.database.migrations.Migration"

        var adapted = source

        if (adapted.startsWith(packageDeclaration) && !adapted.contains(importLine)) {
            adapted = adapted.replaceFirst(
                packageDeclaration,
                "$packageDeclaration\n\n$importLine"
            )
        }

        if (connectionName != null) {
            val classDeclarationPattern = Regex("""class\s+\w+\s*:\s*Migration\(\)\s*\{""")
            adapted = classDeclarationPattern.replace(adapted) { match ->
                "${match.value}\n    override val connectionName: String = \"$connectionName\""
            }
        }

        return adapted
    }

    companion object {
        private val CREATE_TABLE_PATTERN = Regex("^create_(.+)_table$")
    }
}
