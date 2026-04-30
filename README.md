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
- DSL y generacion de migraciones;
- `Migrator`, `MigrationRegistry` y `MigrationRepository` para ejecutar y consultar migraciones registradas explicitamente;
- comandos base `migrate`, `migrate:rollback` y `migrate:status`.

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
- `database.connections.<nombre>.driver` define el motor (`pgsql`, `mariadb`, etc.);
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

Hoy el kernel usa pooling con HikariCP por defecto. La app sigue pidiendo
conexiones por nombre igual que antes, pero internamente cada
`DatabaseConnectionConfig` puede mantener su `DataSource` pooled.

Si una app quiere ajustar el pool por conexión, puede definir:

```kotlin
"pool" to mapOf(
    "enabled" to true,
    "minimumIdle" to 1,
    "maximumPoolSize" to 10,
    "idleTimeoutMs" to 120_000,
    "connectionTimeoutMs" to 30_000,
    "maxLifetimeMs" to 600_000
)
```

`DatabaseManager.from(app)` además se cachea por `Application`, así que una app
desktop abierta durante horas no recrea pools nuevos cada vez que alguien pide
el manager otra vez.

## Trabajo Bloqueante y Virtual Threads

El kernel tambien puede registrar un `BlockingTaskRunner` para mover trabajo
bloqueante fuera del hilo de UI o del hilo llamador:

```kotlin
val runner = app.blockingTaskRunner()

runner.submit {
    database.withConnection("main") { connection ->
        // JDBC bloqueante
    }
}
```

La idea no es paralelizar migraciones internamente, sino permitir que procesos
como `migrator.run()` se ejecuten en background sin congelar una app desktop.

Orden actual del kernel para esta capa:

- `kernel.database.pdo.connections`: manager, resolver y configuracion materializada;
- `kernel.database.pdo.drivers`: drivers soportados por el kernel;
- `kernel.database.schema`: DSL canonico de tablas, columnas y constraints;
- `kernel.database.grammars`: traduccion del DSL a SQL por motor;
- `kernel.database.migrations`: runner, repository, discovery, stubs y ciclo de vida.

Dentro del DSL de columnas, la organizacion ahora separa:

- `PortableColumnBlueprintSupport`: tipos comunes o compartidos;
- `PostgresColumnBlueprintSupport`: extensiones exclusivas de PostgreSQL;
- `MariaDbColumnBlueprintSupport`: extensiones exclusivas de MariaDB.

Dentro del DSL de tablas y migraciones, la organizacion tambien quedo partida
por responsabilidades:

- `PortableTableDefinitionDsl` y `PortableTableAlterationDsl`: helpers comunes de tablas;
- `PostgresTableDefinitionDsl` y `PostgresTableAlterationDsl`: extensiones PostgreSQL para constraints e indices;
- `PortableMigrationDsl`: operaciones comunes del ciclo de migracion;
- `PostgresMigrationDsl`: objetos de base de datos y primitivas avanzadas de PostgreSQL;
- `MigrationRepositoryDialect`: diferencias de repository por motor para quoting, metadata y DDL.

Importante:

- hoy el kernel trae soporte oficial para `PostgreSqlDriver` y `MariaDbDriver`;
- el `Migrator` resuelve el dialecto SQL segun la conexion real que use cada migracion;
- una app puede mezclar motores por conexion, por ejemplo `main` en PostgreSQL y `logs` en MariaDB.

Las migraciones de ejemplo del propio kernel ya no viven mezcladas en la raiz:

- `kernel.database.migrations.examples.postgres`
- `kernel.database.migrations.examples.mariadb`

Eso deja separado el core del sistema de migraciones respecto a las muestras por
motor.

### Resolucion De Conexion En Migraciones

La ejecucion de migraciones sigue una precedencia fija, pensada para parecerse
a Laravel:

1. `migration.connectionName`
2. `--database=<nombre>` en el comando
3. `database.default`

Ejemplo:

```kotlin
class CreateAuditLogsTable : Migration() {
    override val connectionName: String = "logs"

    override fun up() {
        create("audit_logs") {
            id().primaryKey()
            string("message").notNull()
            timestampsTz()
        }
    }

    override fun down() {
        dropIfExists("audit_logs")
    }
}
```

Con esa migracion:

- `./kernel migrate` la ejecuta automaticamente en `logs`;
- `./kernel migrate --database=main` tambien la ejecuta en `logs`, porque la migracion manda;
- una migracion sin `connectionName` usa la conexion del comando si existe;
- si tampoco hay `--database`, usa `database.default`.

### Pruebas Live De Base De Datos

El `kernel` incluye pruebas live opcionales para validar conexiones y
migraciones reales contra PostgreSQL y MariaDB.

El framework no fija credenciales hardcodeadas para esas pruebas. Cada
programador debe configurarlas mediante variables de entorno antes de ejecutar:

```text
KERNEL_TEST_PG_HOST
KERNEL_TEST_PG_PORT
KERNEL_TEST_PG_DATABASE
KERNEL_TEST_PG_USERNAME
KERNEL_TEST_PG_PASSWORD

KERNEL_TEST_MARIADB_HOST
KERNEL_TEST_MARIADB_PORT
KERNEL_TEST_MARIADB_DATABASE
KERNEL_TEST_MARIADB_USERNAME
KERNEL_TEST_MARIADB_PASSWORD
```

Si faltan esas variables, las pruebas live se marcan como omitidas con un
mensaje claro indicando qué credenciales faltan.

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
- [x] Runner base de migraciones con conexiones nombradas.
- [x] `Application` como punto central de bootstrap.
- [ ] Container / IoC.
- [x] `ServiceProvider`.
- [ ] `ConsoleKernel`.
- [ ] Carga de `routes/console`.
- [ ] `HttpKernel`.
- [ ] Router `web` y `api`.
- [x] Router `desktop` y `api` para apps desktop internas.
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

## Rutas Desktop y Api

En el estado actual del kernel, `desktop` y `api` tienen responsabilidades
distintas y no deben mezclarse:

- `desktop` es para navegacion visual de una app desktop;
- `desktop` puede entrar por deep links del sistema operativo;
- `desktop` usa un scheme externo configurable, por ejemplo `kernelplayground://`;
- `api` es para enrutamiento interno entre modulos o servicios locales;
- `api` no debe registrarse como deep link del sistema operativo;
- `api` no participa en el flujo de `SingleInstanceHandler`.

En otras palabras:

- solo `desktop` reacciona a links externos y a segunda instancia;
- `api` existe como router interno, no como protocolo publico del SO.

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
