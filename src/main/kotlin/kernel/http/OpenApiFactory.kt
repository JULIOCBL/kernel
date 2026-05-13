package kernel.http

import kernel.foundation.Application
import kernel.routing.ApiRouter

object OpenApiFactory {
    fun build(application: Application, router: ApiRouter): Map<String, Any?> {
        val paths = linkedMapOf<String, MutableMap<String, Any?>>()

        router.routes().forEach { definition ->
            val normalizedPath = if (definition.path.isBlank()) "/" else "/${definition.path}"
            val pathItem = paths.getOrPut(normalizedPath) { linkedMapOf() }
            val requestMetadata = resolveRequestMetadata(application, definition.requestType)

            pathItem[definition.method.lowercase()] = linkedMapOf<String, Any?>().apply {
                put("operationId", definition.name ?: "${definition.method.lowercase()}_${definition.path.replace('/', '_')}")
                put("tags", listOf(normalizedPath.trim('/').substringBefore('/').ifBlank { "root" }))
                put("parameters", extractPathParameters(definition.path))
                put("middleware", definition.middleware)
                if (requestMetadata != null) {
                    put(
                        "requestBody",
                        mapOf(
                            "required" to true,
                            "content" to mapOf(
                                "application/json" to mapOf(
                                    "schema" to requestMetadata.schema
                                )
                            )
                        )
                    )
                    put("x-validation-rules", requestMetadata.rules)
                }
                put(
                    "responses",
                    mapOf(
                        "200" to mapOf("description" to "Respuesta exitosa"),
                        "422" to mapOf("description" to "Error de validacion")
                    )
                )
            }
        }

        return mapOf(
            "openapi" to "3.0.3",
            "info" to mapOf(
                "title" to application.config.string("app.name", "Kernel API"),
                "version" to application.config.string("app.version", "0.1.0")
            ),
            "paths" to paths
        )
    }

    private fun resolveRequestMetadata(
        application: Application,
        requestType: Class<out Request>?
    ): RequestMetadata? {
        if (requestType == null || requestType == Request::class.java) {
            return null
        }

        val kotlinType = requestType.kotlin
        if (!FormRequest::class.java.isAssignableFrom(requestType)) {
            return null
        }

        val constructor = kotlinType.constructors.firstOrNull { constructor ->
            constructor.parameters.size == 1 &&
                constructor.parameters.first().type.classifier == Request::class
        } ?: return null

        val blankRequest = Request(
            app = application,
            method = "POST",
            target = "api://openapi",
            path = "/openapi"
        )

        val formRequest = constructor.call(blankRequest) as FormRequest
        val rules = formRequest.rules()

        return RequestMetadata(
            rules = rules,
            schema = mapOf(
                "type" to "object",
                "properties" to rules.mapValues { (_, definition) ->
                    inferSchema(definition)
                },
                "required" to rules.filterValues { value ->
                    value.split('|').map(String::trim).any { it == "required" }
                }.keys.toList()
            )
        )
    }

    private fun inferSchema(definition: String): Map<String, Any?> {
        val rules = definition.split('|').map(String::trim)
        val type = when {
            "integer" in rules -> "integer"
            "numeric" in rules -> "number"
            "boolean" in rules -> "boolean"
            "file" in rules -> "string"
            else -> "string"
        }

        val schema = linkedMapOf<String, Any?>("type" to type)

        rules.firstOrNull { it.startsWith("min:") }?.substringAfter("min:")?.toIntOrNull()?.let { min ->
            if (type == "string") {
                schema["minLength"] = min
            } else {
                schema["minimum"] = min
            }
        }

        rules.firstOrNull { it.startsWith("max:") }?.substringAfter("max:")?.toIntOrNull()?.let { max ->
            if (type == "string") {
                schema["maxLength"] = max
            } else {
                schema["maximum"] = max
            }
        }

        if ("email" in rules) {
            schema["format"] = "email"
        }

        if ("file" in rules) {
            schema["format"] = "binary"
        }

        return schema
    }

    private fun extractPathParameters(path: String): List<Map<String, Any?>> {
        return Regex("\\{([^}]+)\\}")
            .findAll(path)
            .map { match ->
                mapOf(
                    "name" to match.groupValues[1],
                    "in" to "path",
                    "required" to true,
                    "schema" to mapOf("type" to "string")
                )
            }
            .toList()
    }

    private data class RequestMetadata(
        val rules: Map<String, String>,
        val schema: Map<String, Any?>
    )
}
