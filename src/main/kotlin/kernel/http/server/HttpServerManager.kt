package kernel.http.server

import kernel.foundation.Application
import kernel.foundation.ApplicationProcessLock
import kernel.routing.ApiRouter

object HttpServerManager {
    private const val SERVER_CONFIG_KEY = "services.http.server.instance"
    private const val SERVER_URLS_CONFIG_KEY = "services.http.server.urls"

    fun ensureStarted(
        application: Application,
        requestedPort: Int? = null
    ): String {
        synchronized(application) {
            val existingUrl = application.config.string(SERVER_URLS_CONFIG_KEY).trim()
            val existingServer = application.config.get(SERVER_CONFIG_KEY) as? KernelHttpServer

            if (existingServer != null && existingUrl.isNotBlank()) {
                return existingUrl
            }

            val host = application.config.string("app.routing.api.host", "0.0.0.0")
            val port = requestedPort ?: application.config.int("app.routing.api.port", 8080)
            val backlog = application.config.int("app.routing.api.backlog", 128)
            val router = application.config.get("services.routes.api.router") as? ApiRouter
                ?: error("ApiRouter no esta registrado en services.routes.api.router.")

            val server = KernelHttpServer(application, router)
            server.start(host = host, port = port, backlog = backlog)

            val url = HttpServerAddressResolver.resolveAccessibleUrls(host, port).joinToString(" | ")
            application.config.set(SERVER_CONFIG_KEY, server)
            application.config.set(SERVER_URLS_CONFIG_KEY, url)

            return url
        }
    }

    fun stop(application: Application): Boolean {
        synchronized(application) {
            val existingServer = application.config.get(SERVER_CONFIG_KEY) as? KernelHttpServer
            if (existingServer != null) {
                existingServer.close()
                application.config.set(SERVER_CONFIG_KEY, null)
                application.config.set(SERVER_URLS_CONFIG_KEY, null)
                HttpServerRuntime.clearState(application)
                return true
            }

            val state = HttpServerRuntime.readState(application) ?: return false
            val pid = ApplicationProcessLock.readPid(application.basePath) ?: return false
            if (state.pid != pid) {
                return false
            }
            if (!ApplicationProcessLock.isAlive(pid)) {
                ApplicationProcessLock.clear(application.basePath, pid)
                HttpServerRuntime.clearState(application)
                return false
            }

            val stopped = ProcessHandle.of(pid).orElse(null)?.destroy() == true
            if (!stopped) {
                ProcessHandle.of(pid).orElse(null)?.destroyForcibly()
            }
            if (!stopped && ApplicationProcessLock.isAlive(pid)) {
                return false
            }

            application.config.set(SERVER_URLS_CONFIG_KEY, null)
            ApplicationProcessLock.clear(application.basePath, pid)
            HttpServerRuntime.clearState(application)
            return true
        }
    }

    fun restart(
        application: Application,
        requestedPort: Int? = null
    ): String {
        synchronized(application) {
            stop(application)
            return ensureStarted(application, requestedPort)
        }
    }

    fun currentUrl(application: Application): String? {
        val inMemory = application.config.string(SERVER_URLS_CONFIG_KEY).trim().takeIf(String::isNotBlank)
        if (inMemory != null) {
            return inMemory
        }

        return HttpServerRuntime.readState(application)
            ?.urls
            ?.takeIf(List<String>::isNotEmpty)
            ?.joinToString(" | ")
    }
}
