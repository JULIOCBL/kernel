package kernel.command.commands

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
class MakeMigrationCommand(
    private val stubFactory: MigrationStubFactory = MigrationStubFactory()
) : Command {
    override val name: String = "make:migration"

    override fun execute(input: CommandInput): CommandResult {
        val migrationName = input.argument(0)
            ?: throw IllegalArgumentException(
                "Debes indicar el nombre de la migracion. Ejemplo: make:migration create_users_table"
            )

        val (template, tableName) = resolveTemplate(input, migrationName)
        val stub = stubFactory.create(
            MigrationStubRequest(
                template = template,
                name = migrationName,
                tableName = tableName
            )
        )

        val migrationsDirectory = resolveMigrationsDirectory(input.workingDirectory)
        Files.createDirectories(migrationsDirectory)

        val targetFile = migrationsDirectory.resolve(stub.fileName)

        require(!Files.exists(targetFile)) {
            "La migracion `${stub.fileName}` ya existe en $migrationsDirectory."
        }

        Files.writeString(targetFile, stub.source, StandardCharsets.UTF_8)

        return CommandResult(
            exitCode = 0,
            message = "Migracion creada: $targetFile"
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

    private fun resolveMigrationsDirectory(workingDirectory: Path): Path {
        return workingDirectory.resolve("src/main/kotlin/kernel/database/migrations")
    }

    companion object {
        private val CREATE_TABLE_PATTERN = Regex("^create_(.+)_table$")
    }
}
