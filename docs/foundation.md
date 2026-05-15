# Capa: Foundation

La capa Foundation es el núcleo del framework. Gestiona el ciclo de vida de la aplicación, la carga de configuración, variables de entorno y el registro de servicios mediante Providers.

## Application (`app`)

La clase `Application` es el punto central. Generalmente se accede a ella mediante el helper global `app()`.

### Funciones Principales
- `path(relativePath)`: Resuelve una ruta absoluta desde la raíz del proyecto.
- `environment()`: Devuelve el entorno actual (`local`, `production`, etc.).
- `isBooted()`: Indica si los providers ya han sido arrancados.
- `boot()`: Inicia la fase de arranque de todos los providers.

### Registro de Providers
```kotlin
app.register(MyServiceProvider(app))
// O mediante factory para evitar instanciación innecesaria
app.register { app -> MyServiceProvider(app) }
```

---

## Configuración (`app.config`)

El Kernel usa una estructura de datos jerárquica para la configuración, accesible mediante "dot notation".

### Métodos de Lectura
- `get(key, default)`: Obtiene el valor original (Any?).
- `string(key, default)`: Obtiene el valor como String.
- `int(key, default)`: Obtiene el valor como Int.
- `bool(key, default)`: Obtiene el valor como Boolean (soporta "true", "1", "yes", "on").
- `map(key)`: Obtiene un sub-nodo como un Mapa de Kotlin.
- `has(key)`: Verifica si una clave existe.

### Métodos de Escritura
- `set(key, value)`: Define un valor en tiempo de ejecución.
- `merge(namespace, map)`: Fusiona un mapa entero dentro de un prefijo.

### Overrides Temporales
Ideal para tests o cambios momentáneos de contexto:
```kotlin
app.config.withTemporaryOverrides(mapOf("db.connection" to "test")) {
    // Aquí la conexión será "test"
    val conn = app.config.string("db.connection")
}
// Al salir del bloque, vuelve a su valor original
```

---

## Variables de Entorno (`app.env`)

Carga valores desde el archivo `.env` y el sistema operativo. **El sistema operativo siempre tiene prioridad.**

### Métodos
- `get(key)`: Obtiene el valor crudo.
- `string(key, default)`: Valor como String.
- `int(key, default)`: Valor como Int.
- `bool(key, default)`: Valor como Booleano.
- `has(key)`: Verifica existencia.

---

## Service Providers

Son las clases encargadas de registrar servicios en el sistema.

### Implementación
```kotlin
class MyServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        // Fase 1: Registrar configuraciones o servicios simples
        app.loadConfig(MyConfig)
    }

    override fun boot() {
        // Fase 2: Ejecutar lógica que dependa de otros providers ya registrados
        val db = app.config.get("database")
    }
}
```
