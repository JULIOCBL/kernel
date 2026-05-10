package kernel.http

import kernel.database.orm.ModelNotFoundException

open class ExceptionHandler {
    open fun render(request: Request, error: Throwable): KernelResponse {
        return when (error) {
            is ValidationException -> JsonResponse(
                payload = mapOf(
                    "status" to "error",
                    "message" to "Los datos enviados no pasaron la validacion.",
                    "errors" to error.errors
                ),
                status = 422
            )

            is AuthorizationException -> JsonResponse(
                payload = mapOf(
                    "status" to "error",
                    "message" to (error.message ?: "No autorizado.")
                ),
                status = 403
            )

            is IllegalArgumentException -> JsonResponse(
                payload = mapOf(
                    "status" to "error",
                    "message" to (error.message ?: "Peticion invalida.")
                ),
                status = 400
            )

            is ModelNotFoundException -> JsonResponse(
                payload = mapOf(
                    "status" to "error",
                    "message" to (error.message ?: "Recurso no encontrado.")
                ),
                status = 404
            )

            else -> JsonResponse(
                payload = mapOf(
                    "status" to "error",
                    "message" to (error.message ?: "Fallo interno al procesar la petición API.")
                ),
                status = 500
            )
        }
    }
}
