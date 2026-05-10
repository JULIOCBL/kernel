# Lang y Localizacion

Esta guia documenta la capa de localizacion actual del `kernel` y, en
particular, estas piezas:

- `kernel.lang.LangFile`
- `kernel.lang.LangStore`
- helpers globales:
  - `lang(...)`
  - `trans(...)`
  - `` `__`(...) ``

La meta es ofrecer una experiencia cercana a Laravel, pero manteniendo el
enfoque compilado en Kotlin que ya usa el framework para configuracion.

## Idea General

La localizacion funciona asi:

1. la app define archivos Kotlin que implementan `LangFile`
2. `Application` carga esos archivos en `LangStore`
3. `HttpKernel` detecta el locale por request
4. `FormRequest` usa traducciones por defecto para mensajes de validacion
5. la app puede traducir manualmente con `lang`, `trans` o `` `__` ``

## LangFile

Cada archivo de idioma declara:

- `locale`
- `namespace`
- `load()`

Ejemplo:

```kotlin
object ValidationLangEs : LangFile {
    override val locale: String = "es"
    override val namespace: String = "validation"

    override fun load(): Map<String, Any?> {
        return mapOf(
            "required" to "El campo :attribute es obligatorio.",
            "min" to "El campo :attribute debe tener al menos :min caracteres.",
            "attributes" to mapOf(
                "email" to "correo"
            )
        )
    }
}
```

## LangStore

`LangStore` materializa los archivos cargados en memoria y resuelve claves con:

- dot notation
- fallback por locale base
- reemplazos dinamicos

Ejemplos:

```kotlin
val translated = app.lang.translate(
    key = "validation.required",
    locale = "es",
    replacements = mapOf("attribute" to "correo")
)
```

```kotlin
val translated = app.lang.translate(
    key = "auth.failed",
    locale = "es-MX",
    replacements = mapOf("user" to "ada@example.com"),
    fallbackLocale = "en"
)
```

## Carga En Application

`Application` ahora soporta:

- `loadLang(file: LangFile)`
- `loadLang(vararg files: LangFile)`
- `loadLang(files: Iterable<LangFile>)`

Ejemplo simple:

```kotlin
app.loadLang(
    ValidationLangEs,
    AuthLangEs
)
```

Ejemplo recomendado cuando la lista crece:

```kotlin
object AppLangFiles {
    val all: List<LangFile> = listOf(
        ValidationLangEs,
        AuthLangEs,
        ValidationLangEn,
        AuthLangEn
    )
}
```

```kotlin
app.loadLang(AppLangFiles.all)
```

## Helpers Globales

Con `ApplicationRuntime` inicializado, puedes usar:

- `lang(...)`
- `trans(...)`
- `` `__`(...) ``

Ejemplo:

```kotlin
val message = lang(
    "auth.failed",
    replacements = mapOf("user" to "ada@example.com")
)
```

```kotlin
val message = trans(
    "validation.required",
    replacements = mapOf("attribute" to "correo")
)
```

En Kotlin, `__` debe llamarse escapado:

```kotlin
val message = `__`(
    "validation.min",
    replacements = mapOf(
        "attribute" to "nombre",
        "min" to 3
    )
)
```

## Locale Activo

El locale activo puede venir de dos lugares:

1. cabecera `Accept-Language`
2. fallback `app.locale`

`HttpKernel` resuelve el locale y lo guarda dentro del `Request`.

Entonces desde middlewares, controladores o `FormRequest` puedes consultar:

```kotlin
val locale = request.locale()
```

Si no hay cabecera, el kernel usa:

- `app.locale`
- y luego `app.fallback_locale` durante resolucion de traducciones

## Accept-Language

El `HttpKernel` soporta cabeceras como:

```text
Accept-Language: es-MX,es;q=0.9,en;q=0.8
```

Comportamiento:

- intenta primero el locale exacto (`es-mx`)
- luego el locale base (`es`)
- si no existe, usa `app.locale`

Esto permite cargar solo `es` y aun asi resolver peticiones `es-MX`.

## Validacion Automatica

`FormRequest` busca mensajes asi:

1. `messages()["field.rule"]`
2. `messages()["field"]`
3. `validation.custom.field.rule`
4. `validation.rule`
5. fallback hardcoded del kernel

Tambien intenta traducir nombres de atributos desde:

- `validationAttributes()`
- `validation.attributes.<field>`

Ejemplo:

```kotlin
object ValidationLangEn : LangFile {
    override val locale: String = "en"
    override val namespace: String = "validation"

    override fun load(): Map<String, Any?> {
        return mapOf(
            "required" to "The :attribute field is required.",
            "attributes" to mapOf(
                "email" to "email"
            )
        )
    }
}
```

Con eso, un `FormRequest` que no sobrescriba `messages()` ya puede devolver:

```text
The email field is required.
```

## Reemplazos Dinamicos

El motor reemplaza tokens estilo Laravel:

- `:attribute`
- `:min`
- `:max`
- `:user`
- cualquier otra clave que envies en `replacements`

Ejemplo:

```kotlin
lang(
    "auth.failed",
    replacements = mapOf("user" to "ada@example.com")
)
```

Si la traduccion contiene:

```text
No pudimos autenticar a :user.
```

el resultado sera:

```text
No pudimos autenticar a ada@example.com.
```

## Convencion Recomendada

Para mantener el proyecto ordenado:

- un archivo Kotlin por namespace
- una carpeta por locale
- un catalogo central `AppLangFiles`

Ejemplo:

```text
src/main/kotlin/playground/lang/es/ValidationLang.kt
src/main/kotlin/playground/lang/es/AuthLang.kt
src/main/kotlin/playground/lang/en/ValidationLang.kt
src/main/kotlin/playground/lang/en/AuthLang.kt
src/main/kotlin/playground/lang/AppLangFiles.kt
```

## Limitaciones Actuales

La capa ya es util y rapida, pero todavia no cubre todo Laravel:

- no hay pluralizacion avanzada
- no hay cargador desde JSON o PHP externos
- no hay fallback por cadena de multiples locales configurable por request
- no hay selector automatico por namespace y dominio regional complejo

La base actual prioriza:

- resolucion rapida en memoria
- dot notation
- integracion nativa con `FormRequest`
- ergonomia clara para apps Kotlin
