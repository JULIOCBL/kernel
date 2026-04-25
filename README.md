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
- utilidades `dump()` y `dd()` para depuracion de consola;
- introspeccion segura de objetos para `dump()` y `dd()` con limites de profundidad y truncado;
- DSL y generacion de migraciones.

Puedes ajustar esos limites en `kernel.debug.DebugConfig`.
Por defecto vienen en `null`, asi que `dump()` y `dd()` no recortan nada.

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
