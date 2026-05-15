# Capa: Security (Kernel Secure Runtime)

Esta capa proporciona un puente hacia un runtime nativo seguro (KSR) escrito en Rust, encargado de operaciones críticas de identidad y cifrado que no deben delegarse enteramente a la JVM.

## Identidad de Hardware (`HardwareIdResolver`)

Resuelve un identificador único del host con persistencia garantizada. Si detecta que el entorno es efímero (como un contenedor sin volúmenes persistentes), falla preventivamente para evitar la pérdida de acceso a datos cifrados.

### Uso
```kotlin
val hwId = HardwareIdResolver.currentId()
```

### Orígenes de Identidad
- **macOS**: `IOPlatformUUID` vía `ioreg`.
- **Linux**: `/etc/machine-id` o `/var/lib/dbus/machine-id`.
- **Windows**: UUID de `csproduct` vía `wmic` o PowerShell.

---

## Secure Runtime (`SecureRuntime`)

Es el puente JNI (Java Native Interface) hacia la librería nativa. Permite inyectar y recuperar fragmentos de claves sensibles que solo residen en la memoria protegida del runtime nativo.

### Funciones Principales
- `injectFragmentB(fragment: ByteArray)`: Inyecta la segunda parte de una llave maestra.
- `getFragmentB()`: Recupera el fragmento almacenado.

---

## Ensamblador de Llaves (`KeyAssembler`)

Combina la identidad del hardware con los fragmentos del runtime para generar llaves de cifrado finales.

---

## Diagnóstico Seguro

El comando `./kernel secure:status` permite verificar el estado del runtime sin exponer datos sensibles:

- Disponibilidad del binario nativo.
- Resultado de la carga JNI.
- Sistema operativo detectado.
- Tamaño del fragmento B almacenado.
