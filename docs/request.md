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

- `all()`: Devuelve todo el input combinado (Body + Query + Route Params).
- `input(key, default)`: Busca en body, query y route params.
- `string(key, default)`
- `int(key, default)`
- `long(key, default)`
- `double(key, default)`
- `boolean(key, default)`: Soporta "true", "1", "yes", "on" como `true`.
- `array(key)` / `list(key)`: Convierte un String separado por comas en `List<String>`.
- `map(key)`: Convierte un String formato JSON `{...}` o `key:val,key2:val2` en `Map<String, String>`.
- `enum(key, KClass<E>, default)`: Convierte el input a un valor de un Enum.
- `only(vararg keys)`
- `except(vararg keys)`
- `collect()` / `collect(key)`
- `keys()`

Ejemplo:

```kotlin
val name = request.string("name")
val page = request.int("page", 1)
val active = request.boolean("active", false)
val tags = request.array("tags")
val metadata = request.map("metadata")
val status = request.enum("status", UserStatus::class)
```

### Query, Body y JSON

- `query()`: Todos los parámetros de la URL.
- `query(key, default)`
- `post()`: Todo el cuerpo de la petición.
- `post(key, default)`
- `json()`: Sinónimo de `post()`.
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
- `accepts(type)` / `accepts(list)`: Verifica si el cliente acepta ciertos tipos de contenido.
- `acceptsJson()`
- `expectsJson()`
- `wantsJson()`
- `prefers(list)`: Elige el mejor tipo de contenido de una lista según la cabecera `Accept`.
- `bearerToken()`: Extrae el token de la cabecera `Authorization: Bearer ...`.

Ejemplo:

```kotlin
val locale = request.header("x-locale", "es")
val token = request.bearerToken()

if (request.acceptsJson()) {
    // ...
}
```

### Presencia de Valores

- `has(key)`: Existe y no está vacío.
- `hasAny(keys)`
- `filled(key)`: Existe y no es nulo/blanco.
- `isNotFilled(key)` / `isNotFilled(keys)`
- `missing(key)`: No está presente en el input.
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

- `merge(map)`: Agrega o sobreescribe valores en el cuerpo de la petición.
- `mergeIfMissing(map)`: Agrega solo si la clave no existe.

Ejemplo:

```kotlin
request.merge(mapOf("tenant_id" to "15"))
request.mergeIfMissing(mapOf("locale" to "es"))
```

### URL y Segmentos

- `method()`: Obtiene el verbo HTTP (GET, POST, etc.).
- `isMethod("POST")`: Verifica el verbo.
- `path()`: Devuelve la ruta sin el slash inicial.
- `url()`: URL base sin query string.
- `fullUrl()`: URL completa incluyendo query string.
- `fullUrlWithQuery(map)`: URL completa fusionando nuevos parámetros.
- `fullUrlWithoutQuery(vararg keys)`: URL completa omitiendo ciertos parámetros.
- `segments()`: Lista de partes de la URL.
- `segment(index, default)`: Obtiene un segmento específico (index inicia en 1).
- `host()`
- `ip()`: Detecta la IP considerando proxies (`X-Forwarded-For`).

Ejemplo:

```kotlin
val fullUrl = request.fullUrl()
val userIdSegment = request.segment(2)
val method = request.method()
```

### Rutas y Atributos

- `route()`: Todos los parámetros resueltos de la ruta.
- `route(key)`: Parámetro específico de la ruta (ej: `{id}`).
- `attribute<T>(key)`: Recupera un atributo dinámico.
- `setAttribute(key, value)`: Define un atributo dinámico.
- `attributes()`
- `user()`: Helper para `attribute("user")`.
- `setUser(user)`: Helper para `setAttribute("user", user)`.
- `locale()` / `setLocale(locale)`: Gestiona el idioma de la petición.

Ejemplo:

```kotlin
val routeUserId = request.route("user")
val currentUser = request.user()
```

## Archivos

`Request` soporta archivos via `UploadedFile`.

Helpers:

- `file(key)`: Obtiene el archivo subido.
- `hasFile(key)`: Verifica si existe un archivo válido en el campo.

`UploadedFile` expone:

- `field`: Nombre del campo en el formulario.
- `originalName`: Nombre original del archivo.
- `contentType`: MIME type.
- `bytes`: Contenido crudo.
- `size`: Tamaño en bytes.
- `extension`: Extensión inferida (jpg, png, pdf, etc.).
- `isValid()`
- `path()`: Ruta temporal si existe.
- `store(directory, disk)`: Guarda el archivo con un nombre UUID.
- `storeAs(directory, fileName, disk)`: Guarda el archivo con nombre específico.

Ejemplo:

```kotlin
val avatar = request.file("avatar")
if (avatar != null && avatar.isValid()) {
    val storedPath = avatar.store("avatars")
}
```

## FormRequest

`FormRequest` extiende `Request` y agrega autorizacion + validacion automática.

Hooks disponibles:

- `authorize()`: Debe devolver `true` para permitir la petición.
- `rules()`: Mapa de reglas de validación.
- `casts()`: Fuerza tipos de datos al usar `validatedTyped()` (ej: `"age" to "int"`).
- `messages()`: Mensajes de error personalizados.
- `validationAttributes()`: Nombres amigables para los campos en los errores.
- `prepareForValidation()`: Se ejecuta antes de validar. Ideal para `merge()`.
- `passedValidation()`: Se ejecuta después de una validación exitosa.

Salida de datos:

- `validated()`: Devuelve un `ValidatedInput` con los valores crudos (`Map<String, String?>`).
- `validatedTyped()`: Devuelve un `ValidatedInput` con valores casteados según las reglas o `casts()`.
- `safe()`: Sinónimo de `validatedTyped()`.

Ejemplo completo:

```kotlin
class StoreTicketRequest(
    request: Request
) : FormRequest(request) {
    override fun prepareForValidation() {
        merge(mapOf("active" to input("active").orEmpty().trim().lowercase()))
    }

    override fun rules(): Map<String, String> {
        return mapOf(
            "user_id" to "required|integer|exists:lab_users,id,main",
            "amount" to "required|numeric",
            "active" to "required|boolean"
        )
    }

    override fun casts(): Map<String, String> {
        return mapOf("amount" to "double")
    }

    override fun messages(): Map<String, String> {
        return mapOf("user_id.exists" to "El usuario indicado no existe.")
    }
}
```

Uso en Controlador:

```kotlin
class TicketController : Controller() {
    fun store(request: StoreTicketRequest) = json(
        payload = mapOf(
            "raw" to request.validated().all(),
            "amount" to request.safe().getValue("amount") // Ya es Double
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
