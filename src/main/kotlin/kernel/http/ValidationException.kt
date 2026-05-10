package kernel.http

class ValidationException(
    val errors: Map<String, List<String>>
) : RuntimeException("Los datos enviados no pasaron la validacion.")

class AuthorizationException(
    message: String = "No autorizado para procesar esta petición."
) : RuntimeException(message)
