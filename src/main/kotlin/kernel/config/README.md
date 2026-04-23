# Configuracion

Este paquete contiene la capa base de configuracion del kernel.

La idea principal es mantener la configuracion cargada en memoria, con acceso
simple por notacion de puntos y sin acoplarla a un origen especifico como JSON,
YAML, properties o una base de datos.

## Clases principales

`ConfigStore` es el almacen principal de configuracion. Permite guardar valores
simples o mapas anidados, leer claves con notacion de puntos y fusionar mapas de
forma profunda.

`ConfigLoader` es el contrato minimo para cargar configuracion desde cualquier
fuente.

`MapConfigLoader` es una implementacion simple para cargar configuracion desde
un `Map` ya construido en memoria.

## Ejemplo basico

```kotlin
import kernel.config.ConfigStore

val config = ConfigStore()

config.set("app.name", "Kernel")
config.set("app.debug", true)
config.set("database.default", "sqlite")

val appName = config.string("app.name")
val debug = config.bool("app.debug")
val database = config.string("database.default")
```

## Fusion de configuracion

`merge` permite combinar configuracion nueva dentro de un namespace. Si ya
existen mapas anidados, se conservan las claves previas y solo se reemplazan las
claves entrantes.

```kotlin
val config = ConfigStore()

config.merge(
    "mail",
    mapOf(
        "default" to "smtp",
        "drivers" to mapOf(
            "smtp" to mapOf(
                "host" to "localhost",
                "port" to 1025
            )
        )
    )
)

val host = config.string("mail.drivers.smtp.host")
val port = config.int("mail.drivers.smtp.port")
```

## Carga desde un loader

```kotlin
import kernel.config.ConfigStore
import kernel.config.MapConfigLoader

val loader = MapConfigLoader(
    mapOf(
        "app" to mapOf(
            "name" to "Kernel",
            "debug" to false
        )
    )
)

val config = ConfigStore(loader.load())
```

## Decisiones de diseno

El store no expone sus mapas internos. Cuando se reciben o devuelven mapas, se
crean copias defensivas para evitar mutaciones externas accidentales.

Las claves usan notacion de puntos porque es simple, legible y suficiente para
este kernel inicial.

Los loaders se mantienen pequenos a proposito. La responsabilidad de parsear un
formato concreto debe vivir en implementaciones futuras, no dentro de
`ConfigStore`.
