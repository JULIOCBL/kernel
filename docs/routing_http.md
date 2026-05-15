# Capa: Routing & HTTP

Esta capa gestiona el flujo de las peticiones desde que entran al sistema hasta que se devuelve una respuesta.

## Rutas (`Route`)

El objeto `Route` permite registrar endpoints de forma declarativa para los esquemas `api://` y `desktop://`.

### Definición de Rutas
```kotlin
Route.get("/users") { params -> "Lista de usuarios" }
Route.post("/users").action(UserController::store)
Route.put("/users/{id}").action(UserController::update)
Route.delete("/users/{id}").action(UserController::destroy)
```

### Grupos de Rutas
Permiten aplicar prefijos, middleware o nombres de forma masiva.
```kotlin
Route.prefix("api/v1").middleware("auth").group {
    Route.get("/profile").action(ProfileController::show)
    Route.post("/settings").action(SettingsController::update)
}
```

### Inyección de Modelos (Model Binding)
Si defines un parámetro en la ruta que coincide con el nombre de un argumento del controlador (y el tipo es un `Model`), el Kernel lo busca automáticamente.
```kotlin
// Ruta: GET /users/{user}
// Controlador:
fun show(user: User) {
    return user // El Kernel ya buscó al usuario por el ID de la URL
}
```

---

## Peticiones (`Request`)

### Datos de Entrada
- `input(key, default)`: Busca en body, query y route params.
- `query(key)`: Solo parámetros de la URL (`?id=1`).
- `post(key)`: Solo parámetros del cuerpo (JSON o Form).
- `header(key)`: Obtiene una cabecera.

### Atributos Dinámicos
Puedes usar la petición para compartir datos entre middlewares y controladores.
```kotlin
request.setAttribute("tenant_id", 123)
val tenantId = request.attribute<Int>("tenant_id")
```

### Gestión de Archivos (`UploadedFile`)
```kotlin
val file = request.file("avatar")
if (file != null && file.isValid()) {
    val path = file.store("avatars/2026") // Guarda en storage con nombre UUID
    // o con nombre específico:
    file.storeAs("avatars", "profile_${user.id}.png")
}
```

---

## Middleware

Permiten interceptar y filtrar peticiones HTTP.

```kotlin
class Authenticate : HttpMiddleware {
    override fun handle(request: Request, next: (Request) -> KernelResponse): KernelResponse {
        if (request.bearerToken() == null) {
            return JsonResponse(mapOf("error" to "No token"), status = 401)
        }
        return next(request)
    }
}
```

---

## Form Requests (Validación)

Clases que encapsulan la lógica de validación y autorización.

```kotlin
class StoreUserRequest(request: Request) : FormRequest(request) {
    override fun authorize() = true
    
    override fun rules() = mapOf(
        "email" to "required|email|unique:users",
        "password" to "required|min:8"
    )
}
```

---

## Respuestas (`Response`)

### Tipos de Respuesta API
- **Automática**: Devolver un `Map`, `List` o `Model` en el controlador genera un JSON 200 automáticamente.
- `JsonResponse(payload, status)`: Control total sobre el JSON.
- `TextResponse(content, status)`: Respuesta de texto plano o HTML crudo.

### Redirecciones (Desktop)
```kotlin
// Dentro de un controlador desktop
return redirect().to("/dashboard")
// o por nombre de ruta
return redirect().route("user.profile", mapOf("id" to "1"))
```

---

## Manejo de Excepciones

El `ExceptionHandler` centraliza cómo se muestran los errores. Puedes extenderlo para personalizar la salida de errores específicos (ej. convertir una excepción de negocio en un error 400 con un formato JSON concreto).

## OpenAPI

El Kernel genera automáticamente documentación OpenAPI 3.0 analizando tus rutas y sus `FormRequest` asociados, incluyendo esquemas de validación y parámetros de ruta.
