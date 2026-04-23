# Entorno

Este paquete contiene la capa base para leer variables de entorno y archivos
`.env`.

La intencion es tener una forma simple y estable de consultar valores de entorno
desde el kernel sin depender directamente de `System.getenv()` en toda la
aplicacion.

## Clases principales

`EnvLoader` carga un archivo `.env` desde disco y devuelve sus valores como
`Map<String, String>`.

`Env` combina los valores cargados desde `.env` con las variables reales del
sistema y ofrece metodos tipados para leerlos.

## Prioridad de valores

Si una misma clave existe en el archivo `.env` y tambien en `System.getenv()`,
gana el valor del sistema.

Esto permite usar `.env` durante desarrollo local y sobrescribir valores en
produccion, CI o contenedores sin cambiar el codigo.

## Formato soportado de `.env`

```dotenv
APP_NAME=Kernel
APP_DEBUG=true
APP_PORT=8080

# Los comentarios empiezan con #
DATABASE_URL=jdbc:sqlite:database.sqlite
```

El parser soporta:

- Lineas `KEY=value`
- Lineas en blanco
- Comentarios que empiezan con `#`
- Espacios alrededor de clave y valor
- Valores envueltos en comillas simples o dobles

## Ejemplo basico

```kotlin
import kernel.env.Env
import kernel.env.EnvLoader

val envValues = EnvLoader(".env").load()
val env = Env(envValues)

val appName = env.string("APP_NAME", "Kernel")
val debug = env.bool("APP_DEBUG")
val port = env.int("APP_PORT", 8080)
```

## Integracion con configuracion

```kotlin
import kernel.config.ConfigStore
import kernel.env.Env
import kernel.env.EnvLoader

val env = Env(EnvLoader(".env").load())
val config = ConfigStore()

config.set("app.name", env.string("APP_NAME", "Kernel"))
config.set("app.debug", env.bool("APP_DEBUG"))
config.set("app.port", env.int("APP_PORT", 8080))
```

## Decisiones de diseno

`Env` cachea los valores al construirse. Esto evita lecturas repetidas y hace
que la configuracion de entorno sea estable durante la ejecucion.

`EnvLoader` devuelve un mapa vacio si el archivo no existe. Asi el archivo
`.env` puede ser opcional y el sistema puede depender solo de variables reales
del sistema cuando sea necesario.
