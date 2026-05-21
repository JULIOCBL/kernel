package kernel.command.commands

import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult
import kernel.foundation.Application
import kernel.foundation.ApplicationProcessLock
import kernel.http.server.HttpServerRuntime

class StopApiCommand(
    private val app: Application
) : Command {
    override val name: String = "stop:api"
    override val description: String =
        "Detiene la API HTTP local activa. Ejemplo: ./kernel stop:api"
    override val usage: String = "stop:api"

    override fun execute(input: CommandInput): CommandResult {
        val state = HttpServerRuntime.readState(app)
            ?: return CommandResult(
                exitCode = 1,
                message = "No existe un estado activo de API local para esta aplicacion."
            )
        val port = state.port
        val pid = ApplicationProcessLock.readPid(app.basePath)
            ?: return CommandResult(
                exitCode = 1,
                message = "No se encontro un archivo .pid para esta aplicacion en `${app.basePath}`."
            )

        if (state.pid != pid) {
            return CommandResult(
                exitCode = 1,
                message = "El archivo .pid y el estado de la API no coinciden para esta aplicacion."
            )
        }

        if (!ApplicationProcessLock.isAlive(pid)) {
            ApplicationProcessLock.clear(app.basePath, pid)
            HttpServerRuntime.clearState(app)
            return CommandResult(
                exitCode = 1,
                message = "El proceso registrado en .pid ($pid) ya no esta activo."
            )
        }

        val stopped = ProcessHandle.of(pid).orElse(null)?.destroy() == true
        if (!stopped) {
            ProcessHandle.of(pid).orElse(null)?.destroyForcibly()
        }

        if (!stopped && ApplicationProcessLock.isAlive(pid)) {
            return CommandResult(
                exitCode = 1,
                message = "No se pudo detener el proceso activo con PID $pid."
            )
        }

        ApplicationProcessLock.clear(app.basePath, pid)
        HttpServerRuntime.clearState(app)

        return CommandResult(
            exitCode = 0,
            message = "Proceso local detenido (PID $pid, puerto $port)."
        )
    }
}
