# Providers En Kernel

Los `ServiceProvider` son el punto de extension principal para inicializar una
aplicacion que consume `kernel`.

Su rol es parecido al de Laravel:

- registrar configuracion;
- preparar servicios;
- definir decisiones de arranque de la app;
- ejecutar inicializacion final cuando toda la app ya termino de registrarse.

## Ciclo De Vida

Un provider tiene dos fases:

### `register()`

Se usa para registrar cosas que la app necesita conocer temprano.

Ejemplos tipicos:

- cargar archivos de configuracion;
- registrar bindings cuando exista container;
- preparar servicios base;
- declarar flags o defaults de aplicacion.

### `boot()`

Se ejecuta cuando la aplicacion ya termino de registrar todos sus providers.

Ejemplos tipicos:

- inicializacion final de servicios;
- validaciones de arranque;
- hooks que dependen de otros providers ya registrados;
- wiring que necesita el estado final de configuracion.

## Contrato Base

```kotlin
abstract class ServiceProvider(
    protected val app: Application
) {
    open fun register() {
    }

    open fun boot() {
    }
}
```

`app` es la instancia explicita de `Application` que el provider puede usar para
leer `env`, `config`, rutas y demas decisiones del arranque.

## Ejemplo Basico

```kotlin
class AuditServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        app.config.set("audit.enabled", true)
    }

    override fun boot() {
        app.config.set("audit.booted", true)
    }
}
```

## Registro Individual

Si quieres registrar un provider manualmente:

```kotlin
val app = Application.bootstrap(basePath)
    .register(::AuditServiceProvider)
    .boot()
```

`Application.register(...)` evita duplicados por tipo. Si registras dos veces el
mismo provider, solo se usa una instancia.

## Registro Declarativo

Para acercarnos al estilo Laravel, `kernel` soporta listas de factories de
providers mediante `ProviderFactory` y `Application.registerAll(...)`.

```kotlin
val providers: List<ProviderFactory> = listOf(
    providerFactory(::AppServiceProvider),
    providerFactory(::AuditServiceProvider),
    providerFactory(::RouteServiceProvider)
)
```

```kotlin
val app = Application.bootstrapRuntime(basePath)
    .registerAll(providers)
    .boot()
```

Ese patron permite mover la lista oficial de providers a un bootstrap central y
evita instanciar providers duplicados antes de descartarlos.

Importante: `bootstrapRuntime(...)` asume una sola app por proceso. Si un test,
tool o flujo necesita construir dos `Application` distintas en el mismo JVM, no
deberia montarse sobre el runtime global ni sobre helpers como `config()` o
`env()`. En ese caso, prefiere `Application.bootstrap(...)` y usa
`app.config` / `app.env` explicitamente.

## Recomendaciones

- usa `register()` para configuracion y preparacion temprana;
- usa `boot()` para trabajo que depende de otros providers ya registrados;
- evita meter logica de negocio de dominio dentro de providers;
- evita efectos secundarios pesados en `register()`;
- si un provider necesita muchos detalles de dominio, probablemente ese trabajo
  deberia vivir en otro servicio y el provider solo deberia conectarlo.

## Convencion Recomendada Para Apps

En apps consumidoras como `kernel-playground`, la lista de providers deberia
vivir en un bootstrap central, por ejemplo `playground.bootstrap.AppBootstrap`.

Asi el punto de entrada principal solo construye la app y no conoce los
detalles de cada provider.
