# Capa: Service Providers

Los `ServiceProvider` son las piezas centrales del arranque del framework. Son responsables de configurar y "conectar" los servicios antes de que la aplicación empiece a procesar peticiones.

## Estructura de un Provider

```kotlin
class MyServiceProvider(app: Application) : ServiceProvider(app) {
    /**
     * Fase 1: Registro.
     * Úsala para cargar configuraciones o registrar servicios que no
     * dependan de otros providers.
     */
    override fun register() {
        app.loadConfig(MyConfig)
    }

    /**
     * Fase 2: Arranque.
     * Úsala para ejecutar lógica que necesite que todos los demás
     * providers ya hayan registrado sus servicios.
     */
    override fun boot() {
        val db = app.config.get("database")
        // Lógica de inicialización final...
    }
}
```

---

## Registro de Providers

Existen dos formas de registrar providers:

### 1. Registro Manual
```kotlin
app.register(MyServiceProvider(app))
```

### 2. Registro mediante Factories (Recomendado)
Permite al Kernel instanciar los providers de forma diferida.
```kotlin
val providers = listOf(
    providerFactory(::DatabaseServiceProvider),
    providerFactory(::RouteServiceProvider)
)
app.registerAll(providers)
```

---

## Providers Incluidos por Defecto

- `DatabaseServiceProvider`: Configura el pool de conexiones y el Migrator.
- `RouteServiceProvider`: Carga los archivos de rutas de la aplicación.
- `MailServiceProvider`: Configura el transporte de correos.
- `BlockingTaskServiceProvider`: Registra el ejecutor de tareas bloqueantes.

---

## Recomendaciones

1. **Simplicidad**: El método `register()` debe ser rápido y sin efectos secundarios pesados.
2. **Dependencias**: Usa `boot()` si tu lógica depende de la configuración registrada por otro provider.
3. **Organización**: Crea un provider por cada gran módulo o funcionalidad de tu aplicación (ej: `AuthServiceProvider`, `PaymentServiceProvider`).
