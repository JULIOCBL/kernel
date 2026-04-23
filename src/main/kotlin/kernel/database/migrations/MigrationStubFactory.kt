package kernel.database.migrations

import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.text.Charsets.UTF_8

/**
 * Parametros para construir un stub de migracion.
 */
data class MigrationStubRequest(
    val template: MigrationStubTemplate = MigrationStubTemplate.BLANK,
    val name: String? = null,
    val tableName: String? = null,
    val packageName: String = "kernel.database.migrations"
)

/**
 * Resultado listo para persistir en disco.
 */
data class MigrationStub(
    val className: String,
    val fileName: String,
    val source: String
)

/**
 * Genera stubs Kotlin para nuevas migraciones.
 */
class MigrationStubFactory(
    private val clock: Clock = Clock.systemUTC()
) {
    fun create(request: MigrationStubRequest): MigrationStub {
        val timestamp = LocalDateTime.now(clock).format(FILE_TIMESTAMP_FORMATTER)
        val migrationName = resolveMigrationName(request)
        val className = "M${timestamp}_$migrationName"
        val fileName = "$className.kt"
        val source = buildSource(
            packageName = request.packageName,
            className = className,
            template = request.template,
            tableName = request.tableName?.let(::normalizeIdentifier)
        )

        return MigrationStub(
            className = className,
            fileName = fileName,
            source = source
        )
    }

    private fun resolveMigrationName(request: MigrationStubRequest): String {
        val explicitName = request.name?.trim().orEmpty()

        if (explicitName.isNotEmpty()) {
            return normalizeIdentifier(explicitName)
        }

        val tableName = request.tableName?.trim().orEmpty()
        require(tableName.isNotEmpty()) {
            "Debes indicar `name` o `tableName` para generar el stub."
        }

        val normalizedTableName = normalizeIdentifier(tableName)

        return when (request.template) {
            MigrationStubTemplate.BLANK ->
                throw IllegalArgumentException("El template BLANK requiere un `name` explicito.")
            MigrationStubTemplate.CREATE_TABLE -> "create_${normalizedTableName}_table"
            MigrationStubTemplate.UPDATE_TABLE -> "update_${normalizedTableName}_table"
            MigrationStubTemplate.DROP_TABLE -> "drop_${normalizedTableName}_table"
        }
    }

    private fun buildSource(
        packageName: String,
        className: String,
        template: MigrationStubTemplate,
        tableName: String?
    ): String {
        val stubTemplate = loadStubTemplate(template)
        val upBody = when (template) {
            MigrationStubTemplate.BLANK -> ""
            MigrationStubTemplate.CREATE_TABLE -> """
                create("$tableName") {
                    id().primaryKey()
                    timestampsTz()
                }
            """.trimIndent()
            MigrationStubTemplate.UPDATE_TABLE -> """
                table("$tableName") {
                }
            """.trimIndent()
            MigrationStubTemplate.DROP_TABLE -> """dropIfExists("$tableName")"""
        }

        val downBody = when (template) {
            MigrationStubTemplate.BLANK -> ""
            MigrationStubTemplate.CREATE_TABLE -> """dropIfExists("$tableName")"""
            MigrationStubTemplate.UPDATE_TABLE -> ""
            MigrationStubTemplate.DROP_TABLE -> """
                create("$tableName") {
                    id().primaryKey()
                    timestampsTz()
                }
            """.trimIndent()
        }

        return stubTemplate
            .replace("{{ packageName }}", packageName)
            .replace("{{ className }}", className)
            .replace("{{ classComment }}", classComment(template, tableName))
            .replace("{{ upComment }}", upComment(template, tableName))
            .replace("{{ downComment }}", downComment(template, tableName))
            .replace("{{ upBody }}", formatBlock(upBody))
            .replace("{{ downBody }}", formatBlock(downBody))
    }

    private fun normalizeIdentifier(value: String): String {
        val normalized = value
            .trim()
            .replace(IDENTIFIER_SEPARATOR_REGEX, "_")
            .replace(IDENTIFIER_EDGE_UNDERSCORE_REGEX, "")
            .lowercase()

        require(normalized.isNotEmpty()) {
            "El identificador de la migracion no puede estar vacio."
        }

        return normalized
    }

    private fun indent(value: String, level: Int): String {
        if (value.isBlank()) {
            return ""
        }

        val prefix = "    ".repeat(level)

        return value.lineSequence()
            .joinToString("\n") { line -> prefix + line }
    }

    private fun formatBlock(body: String): String {
        if (body.isBlank()) {
            return ""
        }

        return indent(body, 2)
    }

    private fun loadStubTemplate(template: MigrationStubTemplate): String {
        val path = "kernel/database/migrations/stubs/${template.stubFileName}"

        return javaClass.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader(UTF_8)
            ?.use { reader -> reader.readText().trimEnd() }
            ?: error("No se encontro el stub de migracion en $path.")
    }

    private fun classComment(template: MigrationStubTemplate, tableName: String?): String {
        return when (template) {
            MigrationStubTemplate.BLANK -> "Migracion base lista para personalizar."
            MigrationStubTemplate.CREATE_TABLE -> "Migracion que crea la tabla `$tableName`."
            MigrationStubTemplate.UPDATE_TABLE -> "Migracion que modifica la tabla `$tableName`."
            MigrationStubTemplate.DROP_TABLE -> "Migracion que elimina la tabla `$tableName`."
        }
    }

    private fun upComment(template: MigrationStubTemplate, tableName: String?): String {
        return when (template) {
            MigrationStubTemplate.BLANK -> "Define las operaciones que aplican la migracion."
            MigrationStubTemplate.CREATE_TABLE -> "Crea la tabla `$tableName`."
            MigrationStubTemplate.UPDATE_TABLE -> "Aplica cambios sobre la tabla `$tableName`."
            MigrationStubTemplate.DROP_TABLE -> "Elimina la tabla `$tableName` si existe."
        }
    }

    private fun downComment(template: MigrationStubTemplate, tableName: String?): String {
        return when (template) {
            MigrationStubTemplate.BLANK -> "Define las operaciones que revierten la migracion."
            MigrationStubTemplate.CREATE_TABLE -> "Elimina la tabla `$tableName` si existe."
            MigrationStubTemplate.UPDATE_TABLE -> "Revierte manualmente los cambios aplicados en `up`."
            MigrationStubTemplate.DROP_TABLE -> "Restaura una estructura basica de la tabla `$tableName`."
        }
    }

    companion object {
        private val FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd_HHmmss")
        private val IDENTIFIER_SEPARATOR_REGEX = Regex("[^a-zA-Z0-9]+")
        private val IDENTIFIER_EDGE_UNDERSCORE_REGEX = Regex("^_+|_+$")
    }
}
