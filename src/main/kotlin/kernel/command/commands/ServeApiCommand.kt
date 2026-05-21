package kernel.command.commands

import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult
import kernel.foundation.Application
import kernel.http.server.HttpServerAddressResolver
import kernel.http.server.HttpServerRuntime
import kernel.http.server.KernelHttpServer
import kernel.routing.ApiRouter

class ServeApiCommand(
    private val app: Application
) : Command {
    override val name: String = "serve:api"
    override val description: String =
        "Inicia la API HTTP local para pruebas. Ejemplo: ./kernel serve:api --port=8080"
    override val usage: String = "serve:api [--host=0.0.0.0] [--port=8080]"

    override fun execute(input: CommandInput): CommandResult {
        val host = input.option("host")
            ?: app.config.string("app.routing.api.host", "0.0.0.0")
        val port = input.option("port")?.toIntOrNull()
            ?: app.config.int("app.routing.api.port", 8080)
        val backlog = app.config.int("app.routing.api.backlog", 128)
        val router = app.config.get("services.routes.api.router") as? ApiRouter
            ?: return CommandResult(
                exitCode = 1,
                message = "ApiRouter no esta registrado en services.routes.api.router."
            )

        KernelHttpServer(app, router).use { server ->
            server.start(host = host, port = port, backlog = backlog)
            val urls = HttpServerAddressResolver.resolveAccessibleUrls(host, port)
            HttpServerRuntime.writeState(
                application = app,
                processId = ProcessHandle.current().pid(),
                host = host,
                port = port,
                urls = urls
            )

            Runtime.getRuntime().addShutdownHook(
                Thread {
                    HttpServerRuntime.clearState(app)
                    server.close()
                }
            )

            println("API local escuchando en ${urls.joinToString(" | ")}")
            urls.forEach { url ->
                println("Prueba: $url/tickets/demo-sale?user_id=7&amount=500")
            }

            server.await()
        }

        return CommandResult(
            exitCode = 0,
            message = ""
        )
    }
}
