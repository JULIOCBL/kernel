# Capa: Utilities & Debugging

Esta capa proporciona herramientas para el desarrollo diario y la gestión de procesos.

## Debugging (`dump` y `dd`)

El Kernel incluye una potente herramienta de inspección de objetos que funciona tanto en consola como en logs.

### Funciones
- `dump(vararg values)`: Imprime una representación visual de los objetos sin detener la ejecución. Muestra la ruta del archivo y la línea exacta donde se llamó.
- `dd(vararg values)`: "Dump and Die". Igual que dump, pero detiene la ejecución del proceso inmediatamente.

### Ejemplo
```kotlin
val user = DB.table("users").first()
dump(user) // Muestra el mapa del usuario con colores y tipos
```

---

## Concurrencia

El Kernel está diseñado sobre **Kotlin Coroutines**. Casi todas las funciones de base de datos y HTTP son `suspend`.

### Recomendaciones
- Evita usar `runBlocking` dentro de controladores o migraciones.
- Usa `DB.transaction` para asegurar que las operaciones asíncronas sean atómicas.

---

## Sistema Operativo (`OS`)

Utilidad para detectar el entorno de ejecución.

- `OS.isWindows()`, `OS.isMac()`, `OS.isLinux()`.
- `OS.tempDir()`: Devuelve la ruta temporal del sistema.

---

## Bloqueo de Instancia (`ApplicationProcessLock`)

Evita que dos instancias de la misma aplicación se ejecuten simultáneamente sobre la misma base de datos, lo cual es crítico para migraciones y procesos de background.

- Se activa automáticamente en `Application.bootstrap()`.
- Crea un archivo `.pid` en la raíz del proyecto.
