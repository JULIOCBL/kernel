package kernel.database.orm

class ModelNotFoundException(
    modelName: String,
    routeKey: String,
    value: String
) : RuntimeException("No se encontro `$modelName` para `$routeKey = $value`.")
