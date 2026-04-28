# Kernel

`kernel` es el framework base del workspace.

Su papel es parecerse al nucleo reusable de Laravel, no a una aplicacion final.

## Objetivo

Convertir `kernel` en una base estandar para construir apps Kotlin con:

- consola;
- ciclo de arranque;
- providers;
- routing;
- middleware;
- requests;
- controllers;
- vistas;
- soporte de base de datos y migraciones.

## Lo Que Ya Existe

- parser y ejecucion basica de comandos;
- registro de comandos;
- carga de `.env`;
- store de configuracion;
- manager ligero de conexiones multiples de base de datos;
- utilidades `dump()` y `dd()` para depuracion de consola;
- introspeccion segura de objetos para `dump()` y `dd()` con limites de profundidad y truncado;
- DSL y generacion de migraciones.

Puedes ajustar esos limites en `kernel.debug.DebugConfig`.
Por defecto vienen en `null`, asi que `dump()` y `dd()` no recortan nada.

## Criterio De Arquitectura

`Application` es una dependencia explicita en el nucleo del framework.

Al mismo tiempo, el workspace puede exponer helpers ergonomicos como `config()`
o `env()` cuando la app ya fue bootstrappeada como singleton estable del
proceso mediante `Application.bootstrapRuntime(...)` o
`application.initializeRuntime()`.

La regla es:

- en codigo reusable del kernel, preferir `Application` explicita;
- en bordes de la app ya inicializada, los helpers globales son validos;
- no existe una "app activa" mutable por hilo ni por scope.

## Runtime Global Del Proceso

`ApplicationRuntime` existe para procesos que operan con una sola app
bootstrappeada, por ejemplo:

- una app desktop;
- una CLI;
- un servidor que arranca una sola vez y comparte una unica configuracion.

Contrato del runtime:

- `bootstrapRuntime(...)` y `ApplicationRuntime.initialize(...)` asumen una sola
  `Application` por proceso;
- no deben tratarse como una fabrica reusable de apps;
- si necesitas dos `Application` distintas dentro del mismo JVM, usa
  `Application.bootstrap(...)` y trabaja explicitamente con la instancia;
- si el bootstrap falla despues de inicializar el runtime, el proceso debe
  considerarse fallido.

En otras palabras: los helpers `app()`, `config()`, `env()` y `basePath()` son
comodidad para una app ya establecida, no una abstraccion para multi-app.

## Providers

`ServiceProvider` es el mecanismo principal de extension del ciclo de arranque.

- `register()` prepara configuracion y servicios tempranos;
- `boot()` ejecuta inicializacion final cuando la app ya registro todos sus providers;
- `Application.registerAll(...)` permite declarar la lista de providers en un bootstrap central.

La guia detallada esta en [src/main/kotlin/kernel/providers/README.md](/Users/julio.billtag/Archivos/test/kernel/src/main/kotlin/kernel/providers/README.md:1).

## Base De Datos

El kernel ya soporta una base inicial para conexiones multiples al estilo
Laravel:

- `database.default` define la conexion por defecto;
- `database.connections.<nombre>` define conexiones nombradas;
- `database.connections.<nombre>.driver` define el motor (`pgsql`, `mysql`, etc.);
- `database.connections.<nombre>.jdbcDriver` permite override del driver JDBC si hace falta;
- `DatabaseManager` resuelve la configuracion y abre conexiones JDBC por nombre.

Ejemplo:

```kotlin
val app = Application.bootstrap(basePath)
    .loadConfig(
        "database",
        mapOf(
            "default" to "primary",
            "connections" to mapOf(
                "primary" to mapOf(
                    "driver" to "pgsql",
                    "url" to "jdbc:postgresql://localhost:5432/app",
                    "username" to "postgres",
                    "password" to "secret"
                ),
                "analytics" to mapOf(
                    "driver" to "pgsql",
                    "url" to "jdbc:postgresql://localhost:5432/analytics",
                    "username" to "postgres",
                    "password" to "secret"
                )
            )
        )
    )

val database = kernel.database.DatabaseManager.from(app)
database.withConnection("analytics") { connection ->
    // usar JDBC aqui
}
```

Por ahora esto abre conexiones JDBC directas. El contrato esta pensado para que
mas adelante podamos introducir pooling o `DataSource` sin romper la forma en
que la app elige conexiones por nombre.

Orden actual del kernel para esta capa:

- `kernel.database.pdo.connections`: manager, resolver y configuracion materializada;
- `kernel.database.pdo.drivers`: drivers soportados por el kernel;
- `kernel.database.postgresql`: piezas especificas de PostgreSQL para schema/migraciones.

Importante:

- hoy el kernel solo trae `PostgreSqlDriver`;
- por lo tanto, tanto conexiones como migraciones/schema estan oficialmente orientadas a `pgsql`;
- cuando agreguemos otro motor, deberia entrar como un nuevo driver dentro de `kernel.database.pdo.drivers`.

## Estructura Actual

Hoy el proyecto contiene principalmente:

```text
src/main/kotlin/kernel/foundation
src/main/kotlin/kernel/command
src/main/kotlin/kernel/config
src/main/kotlin/kernel/database
src/main/kotlin/kernel/env
src/main/kotlin/kernel/providers
src/test/kotlin/kernel
```

## Estructura Objetivo

La estructura objetivo del framework deberia crecer hacia algo asi:

```text
src/main/kotlin/kernel/foundation
src/main/kotlin/kernel/container
src/main/kotlin/kernel/providers
src/main/kotlin/kernel/console
src/main/kotlin/kernel/http
src/main/kotlin/kernel/routing
src/main/kotlin/kernel/middleware
src/main/kotlin/kernel/view
src/main/kotlin/kernel/config
src/main/kotlin/kernel/env
src/main/kotlin/kernel/database
src/test/kotlin/kernel
```

## Checklist Del Framework

- [x] Contrato base de comandos.
- [x] Parser y registro de comandos.
- [x] CLI inicial del kernel.
- [x] Carga de `.env`.
- [x] Config store base.
- [x] Contrato `ConfigFile` para configuracion en Kotlin por namespace.
- [x] Sistema de migraciones y stubs.
- [x] `Application` como punto central de bootstrap.
- [ ] Container / IoC.
- [x] `ServiceProvider`.
- [ ] `ConsoleKernel`.
- [ ] Carga de `routes/console`.
- [ ] `HttpKernel`.
- [ ] Router `web` y `api`.
- [ ] Pipeline de middleware.
- [ ] Base controller.
- [ ] Render de vistas.
- [ ] Requests y validacion.

## Reglas Del Kernel

Para que siga siendo reusable:

- no debe contener clases de negocio de una app concreta;
- no debe depender de `kernel-playground` ni de `venda-simple-pos-core`;
- toda convencion de rutas o carpetas debe ser configurable, pero con defaults estables;
- los comandos del framework deben poder convivir con comandos de la app;
- la generacion de archivos debe permitir cambiar package y ruta de salida;
- el ciclo de arranque debe ser igual para cualquier app consumidora.

## Que Debe Repetirse En Cualquier App

El kernel funcionara mejor si toda app consumidora repite estas piezas:

- carpeta raiz `config/`;
- archivos Kotlin de configuracion, uno por namespace;
- bootstrap de aplicacion;
- lista de providers;
- rutas de consola;
- rutas web y api;
- carpeta de migraciones;
- `.env` y `.env.example`;
- convencion clara para comandos propios.

## Nota De Implementacion

La idea es que `kernel-playground` sea la primera app en validar estas
convenciones. Cuando algo funcione bien ahi, se consolida en `kernel`.
