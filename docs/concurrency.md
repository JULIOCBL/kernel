# Capa: Concurrency (Blocking Tasks)

Aunque el Kernel está construido sobre corrutinas de Kotlin para operaciones no bloqueantes, existen tareas heredadas o de bajo nivel (como JDBC o acceso a disco) que siguen siendo bloqueantes. Esta capa proporciona una forma controlada de delegar ese trabajo.

## Blocking Task Runner (`BlockingTaskRunner`)

Es la interfaz encargada de ejecutar tareas fuera del hilo principal.

### Implementación JVM
El Kernel incluye `JvmBlockingTaskRunner`, que utiliza **Virtual Threads** (Project Loom) por defecto si están disponibles, o un pool de hilos elástico (Cached Thread Pool) en versiones anteriores.

---

## Uso

### Acceso al Runner
```kotlin
val runner = blockingTaskRunner()
```

### Ejecutar y Esperar (Bloqueante)
Ejecuta la tarea en el pool de hilos y bloquea el hilo actual hasta que termine. Útil en contextos que ya son síncronos.
```kotlin
val result = runner.run {
    // Operación pesada de I/O
    "completado"
}
```

### Ejecutar en Segundo Plano (Asíncrono)
Devuelve un `Future<T>` que permite consultar el resultado más tarde.
```kotlin
val future = runner.submit {
    // Operación en background
}
```

---

## Recomendaciones de Diseño

1. **Prioriza Corrutinas**: Si la operación tiene una versión `suspend` nativa, úsala siempre sobre el `BlockingTaskRunner`.
2. **Aislamiento**: Usa el runner para aislar el impacto de librerías externas bloqueantes (como drivers JDBC antiguos).
3. **No abuses**: No uses el runner para tareas puramente de CPU; está optimizado para tareas que pasan mucho tiempo esperando I/O.
