# Request y FormRequest

Esta guia documenta la capa HTTP actual del `kernel` y, en particular, las
clases:

- `kernel.http.Request`
- `kernel.http.FormRequest`
- `kernel.http.UploadedFile`
- `kernel.http.ValidatedInput`

La meta es ofrecer una experiencia cercana a Laravel sin romper el flujo actual
del framework:

1. `LocalApiHttpServer` construye un `Request`
2. `HttpKernel` ejecuta middlewares globales y de ruta
3. el controlador recibe:
   - un `Request`, o
   - un `FormRequest` validado automaticamente

## Donde Se Usa

`Request` es la clase base que atraviesa toda la capa HTTP:

- middlewares HTTP
- `HttpKernel`
- resolucion de rutas API
- controladores
- `FormRequest`

Si un controlador tipa un `FormRequest`, el kernel:

1. resuelve el `Request` actual;
2. instancia el `FormRequest`;
3. ejecuta `prepareForValidation()`;
4. ejecuta `authorize()`;
5. valida `rules()`;
6. si todo sale bien, ejecuta `passedValidation()`;
7. entrega el request validado al controlador.

## Ejemplo Basico

```kotlin
class UserController : Controller() {
    fun store(request: CreateUserRequest) = json(
        payload = mapOf(
            "status" to "ok",
            "name" to request.validated().getValue("name"),
            "email" to request.validated().getValue("email")
        ),
        status = 201
    )
}
```

```kotlin
class CreateUserRequest(
    request: Request
) : FormRequest(request) {
    override fun rules(): Map<String, String> {
        return mapOf(
            "name" to "required|min:3|max:120",
            "email" to "required|email|unique:api_users,email,main"
        )
    }
}
```

## Helpers Disponibles En Request

### Input General

- `all()`
- `input(key, default)`
- `string(key, default)`
- `int(key, default)`
- `long(key, default)`
- `double(key, default)`
- `boolean(key, default)`
- `array(key)`
- `only(vararg keys)`
- `except(vararg keys)`
- `collect()`
- `collect(key)`
- `keys()`

Ejemplo:

```kotlin
val name = request.string("name")
val page = request.int("page", 1)
val active = request.boolean("active", false)
val tags = request.array("tags")
```

### Query, Body y JSON

- `query()`
- `query(key, default)`
- `post()`
- `post(key, default)`
- `json()`
- `json(key, default)`

Ejemplo:

```kotlin
val filters = request.query()
val page = request.query("page")
val payload = request.post()
val name = request.json("name")
```

### Headers y Autorizacion

- `header(key, default)`
- `headers()`
- `accepts(...)`
- `acceptsJson()`
- `expectsJson()`
- `wantsJson()`
- `prefers(listOf(...))`
- `bearerToken()`

Ejemplo:

```kotlin
val locale = request.header("x-locale", "es")
val token = request.bearerToken()

if (request.acceptsJson()) {
    // ...
}
```

### Presencia de Valores

- `has(key)`
- `hasAny(keys)`
- `filled(key)`
- `isNotFilled(key)`
- `isNotFilled(keys)`
- `missing(key)`
- `whenHas(key, callback, default)`
- `whenFilled(key, callback, default)`
- `whenMissing(key, callback, default)`

Ejemplo:

```kotlin
request.whenFilled("search", { value ->
    println("Buscar: $value")
})
```

### Mutacion Del Input

- `merge(mapOf(...))`
- `mergeIfMissing(mapOf(...))`

Ejemplo:

```kotlin
request.merge(mapOf("tenant_id" to "15"))
request.mergeIfMissing(mapOf("locale" to "es"))
```

### URL y Segmentos

- `method()`
- `isMethod("POST")`
- `path()` -> sin slash inicial
- propiedad `path` -> path crudo recibido
- `url()`
- `fullUrl()`
- `fullUrlWithQuery(...)`
- `fullUrlWithoutQuery(...)`
- `segments()`
- `segment(index, default)`
- `host()`
- `ip()`

Ejemplo:

```kotlin
val fullUrl = request.fullUrl()
val userIdSegment = request.segment(2)
val method = request.method()
```

### Rutas y Atributos

- `route()`
- `route(key)`
- `attribute<T>(key)`
- `setAttribute(key, value)`
- `attributes()`
- `user()`
- `setUser(user)`

Ejemplo:

```kotlin
val routeUserId = request.route("user")
val currentUser = request.user()
```

## Archivos

`Request` soporta archivos via `UploadedFile`.

Helpers:

- `file(key)`
- `hasFile(key)`

`UploadedFile` expone:

- `field`
- `originalName`
- `contentType`
- `bytes`
- `size`
- `extension`
- `isValid()`
- `path()`
- `store(directory)`
- `storeAs(directory, fileName)`

Ejemplo:

```kotlin
val avatar = request.file("avatar")
if (avatar != null && avatar.isValid()) {
    val storedPath = avatar.store("avatars")
}
```

## FormRequest

`FormRequest` extiende `Request` y agrega autorizacion + validacion.

Hooks disponibles:

- `authorize()`
- `rules()`
- `casts()`
- `messages()`
- `validationAttributes()`
- `prepareForValidation()`
- `passedValidation()`

Helpers de salida:

- `validated()`
- `validatedTyped()`
- `safe()`

Ejemplo completo:

```kotlin
class StoreTicketRequest(
    request: Request
) : FormRequest(request) {
    override fun prepareForValidation() {
        merge(
            mapOf(
                "active" to input("active").orEmpty().trim().lowercase()
            )
        )
    }

    override fun rules(): Map<String, String> {
        return mapOf(
            "user_id" to "required|integer|exists:lab_users,id,main",
            "amount" to "required|numeric",
            "active" to "required|boolean"
        )
    }

    override fun messages(): Map<String, String> {
        return mapOf(
            "user_id.exists" to "El usuario indicado no existe."
        )
    }
}
```

Uso:

```kotlin
class TicketController : Controller() {
    fun store(request: StoreTicketRequest) = json(
        payload = mapOf(
            "raw" to request.validated(),
            "typed" to request.validatedTyped(),
            "safe" to request.safe().only("user_id", "amount")
        ),
        status = 201
    )
}
```

## Reglas De Validacion Disponibles Hoy

Actualmente `FormRequest` soporta:

- `required`
- `email`
- `integer`
- `numeric`
- `boolean`
- `min`
- `max`
- `unique:table,column,connection`
- `exists:table,column,connection`
- `file`
- `extensions:png,jpg,...`
- `mimes:png,jpg,...`
- `max_file:<kb>`
- `min_file:<kb>`

## Safe Input

`safe()` devuelve un `ValidatedInput`.

API actual:

- `safe().all()`
- `safe().only(...)`
- `safe().except(...)`

Ejemplo:

```kotlin
val safe = request.safe()
val payload = safe.only("name", "email")
```

## Middlewares

Los middlewares HTTP reciben `Request`, no `FormRequest`.

Ejemplo:

```kotlin
class AuthApiMiddleware : HttpMiddleware {
    override fun handle(
        request: Request,
        next: (Request) -> KernelResponse
    ): KernelResponse {
        val token = request.bearerToken()
            ?: return JsonResponse(
                payload = mapOf("status" to "error", "message" to "Token requerido."),
                status = 401
            )

        request.setAttribute("token", token)

        return next(request)
    }
}
```

## Estado Actual Frente a Laravel

La capa ya cubre bastante del flujo diario, pero todavia no es una paridad total
con `Illuminate\Http\Request`.

Lo que sigue pendiente o mas limitado:

- nested input real con dot notation profunda
- cookies y server bags dedicados
- negociacion de contenido mas completa
- colecciones tipo Laravel
- propiedades dinamicas estilo PHP
- validacion avanzada de arrays anidados y reglas compuestas
- `UploadedFile` al nivel completo de Symfony / Laravel

## Recomendacion Practica

Hoy, para trabajar comodo en este kernel:

1. usa `Request` en middlewares;
2. usa `FormRequest` en controladores que validen datos;
3. usa `validatedTyped()` cuando esperes enteros, booleanos o numericos;
4. usa `safe()` para construir payloads confiables;
5. usa `UploadedFile.store(...)` solo cuando el runtime ya haya inyectado `storageRoot`.
