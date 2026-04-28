# AGENTS

## Runtime de Application

- `ApplicationRuntime` asume una sola `Application` por proceso.
- `Application.bootstrapRuntime(...)` es para el arranque real de una app ya
  definitiva, no para fabricar multiples instancias en el mismo JVM.
- Si necesitas dos apps en el mismo proceso, usa `Application.bootstrap(...)`
  y trabaja explicitamente con la instancia en lugar de `app()`, `config()`,
  `env()` o `basePath()`.
- Si un cambio hace que el bootstrap pueda reintentarse dentro del mismo
  proceso, revisa primero si realmente debe seguir usando `ApplicationRuntime`.

## Providers

- `register()` es para configuracion y preparacion temprana.
- `boot()` es para inicializacion final cuando la app ya termino de registrar
  todos sus providers.
- Las listas declarativas de providers viven mejor en un bootstrap central.

## Regla Practica

Antes de tocar bootstrap o helpers globales, confirma si el flujo esperado es:

1. una sola app por proceso;
2. bootstrap una sola vez;
3. proceso fallido si el bootstrap falla.

Si cualquiera de esas tres condiciones no se cumple, no asumas que
`ApplicationRuntime` es la abstraccion correcta.
