package kernel.http

import kernel.database.DB
import kotlinx.coroutines.runBlocking
import kotlin.reflect.full.isSubclassOf

abstract class FormRequest(
    request: Request
) : Request(
    app = request.app,
    method = request.method,
    target = request.target,
    path = request.path,
    queryParams = request.queryParams,
    body = request.body,
    headers = request.headers,
    files = request.files,
    routeParams = request.routeParams,
    rawBody = request.rawBody,
    remoteAddress = request.remoteAddress,
    attributes = request.attributes()
) {
    private var validatedPayload: Map<String, String>? = null
    private var validatedTypedPayload: Map<String, Any?>? = null

    open fun authorize(): Boolean = true

    abstract fun rules(): Map<String, String>

    open fun casts(): Map<String, String> = emptyMap()

    open fun messages(): Map<String, String> = emptyMap()

    open fun validationAttributes(): Map<String, String> = emptyMap()

    open fun prepareForValidation() {}

    open fun passedValidation() {}

    fun validated(): Map<String, String> {
        return validatedPayload ?: all()
    }

    fun validatedTyped(): Map<String, Any?> {
        return validatedTypedPayload ?: validated().mapValues { (field, value) ->
            castValue(field, value)
        }
    }

    fun safe(): ValidatedInput = ValidatedInput(validatedTyped())

    internal fun validateResolved() {
        prepareForValidation()

        if (!authorize()) {
            throw AuthorizationException()
        }

        val errors = linkedMapOf<String, MutableList<String>>()
        val payload = all()

        rules().forEach { (field, definition) ->
            val value = payload[field]
            parseRules(definition).forEach { rule ->
                validateRule(field, value, rule, errors)
            }
        }

        if (errors.isNotEmpty()) {
            throw ValidationException(errors.mapValues { it.value.toList() })
        }

        validatedPayload = payload
        validatedTypedPayload = payload.mapValues { (field, value) ->
            castValue(field, value)
        }
        passedValidation()
    }

    private fun validateRule(
        field: String,
        value: String?,
        rule: ValidationRule,
        errors: MutableMap<String, MutableList<String>>
    ) {
        when (rule.name) {
            "required" -> if (value.isNullOrBlank()) {
                errors.error(
                    field,
                    formatMessage(
                        field,
                        rule.name,
                        replacements = mapOf("attribute" to displayName(field)),
                        fallback = "El campo `${displayName(field)}` es obligatorio."
                    )
                )
            }

            "email" -> if (!value.isNullOrBlank() && !EMAIL_PATTERN.matches(value)) {
                errors.error(
                    field,
                    formatMessage(
                        field,
                        rule.name,
                        replacements = mapOf("attribute" to displayName(field)),
                        fallback = "El campo `${displayName(field)}` debe ser un email valido."
                    )
                )
            }

            "integer" -> if (!value.isNullOrBlank() && value.toIntOrNull() == null) {
                errors.error(
                    field,
                    formatMessage(
                        field,
                        rule.name,
                        replacements = mapOf("attribute" to displayName(field)),
                        fallback = "El campo `${displayName(field)}` debe ser un entero."
                    )
                )
            }

            "numeric" -> if (!value.isNullOrBlank() && value.toDoubleOrNull() == null) {
                errors.error(
                    field,
                    formatMessage(
                        field,
                        rule.name,
                        replacements = mapOf("attribute" to displayName(field)),
                        fallback = "El campo `${displayName(field)}` debe ser numerico."
                    )
                )
            }

            "min" -> {
                val limit = rule.arguments.firstOrNull()?.toIntOrNull() ?: return
                if ((value ?: "").length < limit) {
                    errors.error(
                        field,
                        formatMessage(
                            field,
                            rule.name,
                            replacements = mapOf("attribute" to displayName(field), "min" to limit),
                            fallback = "El campo `${displayName(field)}` debe tener al menos $limit caracteres."
                        )
                    )
                }
            }

            "max" -> {
                val limit = rule.arguments.firstOrNull()?.toIntOrNull() ?: return
                if ((value ?: "").length > limit) {
                    errors.error(
                        field,
                        formatMessage(
                            field,
                            rule.name,
                            replacements = mapOf("attribute" to displayName(field), "max" to limit),
                            fallback = "El campo `${displayName(field)}` no puede tener mas de $limit caracteres."
                        )
                    )
                }
            }

            "unique" -> {
                if (!value.isNullOrBlank()) {
                    validateUnique(field, value, rule, errors)
                }
            }

            "exists" -> {
                if (!value.isNullOrBlank()) {
                    validateExists(field, value, rule, errors)
                }
            }

            "boolean" -> if (!value.isNullOrBlank() && parseBoolean(value) == null) {
                errors.error(
                    field,
                    formatMessage(
                        field,
                        rule.name,
                        replacements = mapOf("attribute" to displayName(field)),
                        fallback = "El campo `${displayName(field)}` debe ser booleano."
                    )
                )
            }

            "file" -> if (file(field) == null) {
                errors.error(
                    field,
                    formatMessage(
                        field,
                        rule.name,
                        replacements = mapOf("attribute" to displayName(field)),
                        fallback = "El archivo `${displayName(field)}` es obligatorio."
                    )
                )
            }

            "extensions", "mimes" -> {
                val uploadedFile = file(field) ?: return
                val allowed = rule.arguments.map { it.lowercase() }
                if (uploadedFile.extension !in allowed) {
                    errors.error(
                        field,
                        formatMessage(
                            field,
                            rule.name,
                            replacements = mapOf(
                                "attribute" to displayName(field),
                                "values" to allowed.joinToString(", ")
                            ),
                            fallback = "El archivo `${displayName(field)}` debe tener una extension valida: ${allowed.joinToString(", ")}."
                        )
                    )
                }
            }

            "max_file" -> {
                val uploadedFile = file(field) ?: return
                val limitKb = rule.arguments.firstOrNull()?.toLongOrNull() ?: return
                if (uploadedFile.size > limitKb * 1024) {
                    errors.error(
                        field,
                        formatMessage(
                            field,
                            rule.name,
                            replacements = mapOf("attribute" to displayName(field), "max" to limitKb),
                            fallback = "El archivo `${displayName(field)}` no puede exceder ${limitKb}KB."
                        )
                    )
                }
            }

            "min_file" -> {
                val uploadedFile = file(field) ?: return
                val limitKb = rule.arguments.firstOrNull()?.toLongOrNull() ?: return
                if (uploadedFile.size < limitKb * 1024) {
                    errors.error(
                        field,
                        formatMessage(
                            field,
                            rule.name,
                            replacements = mapOf("attribute" to displayName(field), "min" to limitKb),
                            fallback = "El archivo `${displayName(field)}` debe medir al menos ${limitKb}KB."
                        )
                    )
                }
            }
        }
    }

    private fun validateUnique(
        field: String,
        value: String,
        rule: ValidationRule,
        errors: MutableMap<String, MutableList<String>>
    ) {
        val table = rule.arguments.getOrNull(0)?.trim().orEmpty()
        val column = rule.arguments.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: field
        val connectionName = rule.arguments.getOrNull(2)?.trim()?.takeUnless(String::isBlank)

        require(IDENTIFIER_PATTERN.matches(table)) {
            "La regla unique para `$field` usa una tabla invalida: `$table`."
        }
        require(IDENTIFIER_PATTERN.matches(column)) {
            "La regla unique para `$field` usa una columna invalida: `$column`."
        }

        val exists = runBlocking {
            DB.withConnection(connectionName) { connection ->
                connection.prepareStatement(
                    "SELECT 1 FROM $table WHERE $column = ? LIMIT 1"
                ).use { statement ->
                    statement.setString(1, value)
                    statement.executeQuery().use { resultSet ->
                        resultSet.next()
                    }
                }
            }
        }

        if (exists) {
            errors.error(
                field,
                formatMessage(
                    field,
                    rule.name,
                    replacements = mapOf("attribute" to displayName(field)),
                    fallback = "El valor del campo `${displayName(field)}` ya existe."
                )
            )
        }
    }

    private fun validateExists(
        field: String,
        value: String,
        rule: ValidationRule,
        errors: MutableMap<String, MutableList<String>>
    ) {
        val table = rule.arguments.getOrNull(0)?.trim().orEmpty()
        val column = rule.arguments.getOrNull(1)?.trim().takeUnless { it.isNullOrBlank() } ?: field
        val connectionName = rule.arguments.getOrNull(2)?.trim()?.takeUnless(String::isBlank)

        require(IDENTIFIER_PATTERN.matches(table)) {
            "La regla exists para `$field` usa una tabla invalida: `$table`."
        }
        require(IDENTIFIER_PATTERN.matches(column)) {
            "La regla exists para `$field` usa una columna invalida: `$column`."
        }

        val exists = runBlocking {
            DB.withConnection(connectionName) { connection ->
                connection.prepareStatement(
                    "SELECT 1 FROM $table WHERE $column = ? LIMIT 1"
                ).use { statement ->
                    statement.setString(1, value)
                    statement.executeQuery().use { resultSet ->
                        resultSet.next()
                    }
                }
            }
        }

        if (!exists) {
            errors.error(
                field,
                formatMessage(
                    field,
                    rule.name,
                    replacements = mapOf("attribute" to displayName(field)),
                    fallback = "El valor del campo `${displayName(field)}` no existe."
                )
            )
        }
    }

    private fun parseRules(definition: String): List<ValidationRule> {
        return definition
            .split('|')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { token ->
                val parts = token.split(':', limit = 2)
                val name = parts.first().trim().lowercase()
                val arguments = parts.getOrNull(1)
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    .orEmpty()
                ValidationRule(name, arguments)
            }
    }

    private fun MutableMap<String, MutableList<String>>.error(field: String, message: String) {
        getOrPut(field) { mutableListOf() } += message
    }

    private fun displayName(field: String): String {
        return validationAttributes()[field]?.trim()?.takeIf(String::isNotBlank)
            ?: translate(
                key = "validation.attributes.$field",
                default = field
            )
    }

    private fun formatMessage(
        field: String,
        rule: String,
        replacements: Map<String, Any?> = emptyMap(),
        fallback: String
    ): String {
        return messages()["$field.$rule"]?.trim()?.takeIf(String::isNotBlank)
            ?: messages()[field]?.trim()?.takeIf(String::isNotBlank)
            ?: translate(
                key = "validation.custom.$field.$rule",
                replacements = replacements,
                default = translate(
                    key = "validation.$rule",
                    replacements = replacements,
                    default = fallback
                )
            )
    }

    private fun translate(
        key: String,
        replacements: Map<String, Any?> = emptyMap(),
        default: String
    ): String {
        return app.lang.translate(
            key = key,
            locale = locale() ?: "en",
            replacements = replacements,
            fallbackLocale = app.config.string("app.fallback_locale", "en"),
            default = default
        )
    }

    private fun castValue(field: String, value: String): Any? {
        val explicitType = casts()[field]?.trim()?.lowercase()
        if (!explicitType.isNullOrBlank()) {
            return castToType(value, explicitType)
        }

        val ruleTypes = parseRules(rules()[field].orEmpty()).map { it.name }
        return when {
            "integer" in ruleTypes -> value.toIntOrNull() ?: value
            "numeric" in ruleTypes -> value.toDoubleOrNull() ?: value
            "boolean" in ruleTypes -> parseBoolean(value) ?: value
            else -> value
        }
    }

    private fun castToType(value: String, type: String): Any? {
        return when (type) {
            "int", "integer" -> value.toIntOrNull() ?: value
            "long" -> value.toLongOrNull() ?: value
            "double", "float", "numeric" -> value.toDoubleOrNull() ?: value
            "boolean", "bool" -> parseBoolean(value) ?: value
            "list", "array" -> value
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
            "map", "object" -> if (value.startsWith("{") && value.endsWith("}")) {
                value
                    .removePrefix("{")
                    .removeSuffix("}")
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .associate { token ->
                        val index = token.indexOf(':')
                        require(index > 0) {
                            "El valor `$value` no puede convertirse a mapa."
                        }
                        token.substring(0, index).trim().trim('"') to
                            token.substring(index + 1).trim().trim('"')
                    }
            } else {
                value
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .associate { token ->
                        val index = token.indexOf(':')
                        require(index > 0) {
                            "El valor `$value` no puede convertirse a mapa."
                        }
                        token.substring(0, index).trim() to token.substring(index + 1).trim()
                    }
            }
            else -> value
        }
            .let { casted ->
                if (type.startsWith("enum:")) {
                    castEnum(value, type.substringAfter("enum:"))
                } else {
                    casted
                }
            }
    }

    private fun parseBoolean(value: String): Boolean? {
        return when (value.trim().lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun castEnum(value: String, qualifiedClassName: String): Any? {
        val enumClass = Class.forName(qualifiedClassName).kotlin
        require(enumClass.isSubclassOf(Enum::class)) {
            "`$qualifiedClassName` no es un enum valido para casts()."
        }

        return (enumClass.java.enumConstants as Array<out Enum<*>>)
            .firstOrNull { constant ->
                constant.name.equals(value.trim(), ignoreCase = true)
            } ?: value
    }

    private data class ValidationRule(
        val name: String,
        val arguments: List<String>
    )

    companion object {
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
        private val IDENTIFIER_PATTERN = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$")
    }
}
