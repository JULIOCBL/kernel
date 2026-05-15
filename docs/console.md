# Capa: Console (CLI)

Esta capa permite extender las funcionalidades del Kernel mediante comandos de terminal.

## Definición de Comandos

Para crear un comando, debes implementar la interfaz `Command`.

```kotlin
class MyCommand : Command {
    override val name: String = "my:command"
    override val description: String = "Descripción breve de lo que hace."
    override val usage: String = "my:command [arg] [--option=value]"

    override fun execute(input: CommandInput): CommandResult {
        val arg = input.argument(0) ?: "default"
        val option = input.option("force") ?: "false"

        // Lógica del comando...

        return CommandResult(
            exitCode = 0,
            message = "Comando ejecutado con éxito."
        )
    }
}
```

---

## Entrada del Comando (`CommandInput`)

Contiene todo lo que el usuario escribió en la terminal.

### Métodos
- `argument(index)`: Obtiene un argumento posicional (ej: `./kernel cmd arg0 arg1`).
- `option(name)`: Obtiene el valor de una opción (ej: `--user=julio`).
- `hasOption(name)`: Verifica si se pasó una opción, aunque no tenga valor.
- `workingDirectory`: Ruta desde donde se invocó el kernel.

---

## Estilos de Salida (`CommandOutputStyle`)

El Kernel incluye una utilidad para dar formato profesional a los mensajes de consola.

```kotlin
// Dentro de execute()
val success = CommandOutputStyle.success("¡Operación completada!")
val error = CommandOutputStyle.error("Algo salió mal.")
val info = CommandOutputStyle.info("Nota importante.")
```

---

## Registro de Comandos

Los comandos deben registrarse en el `CommandRegistry` (generalmente dentro de un `ConsoleServiceProvider`).

```kotlin
val registry = CommandRegistry()
registry.register(MyCommand())
```
