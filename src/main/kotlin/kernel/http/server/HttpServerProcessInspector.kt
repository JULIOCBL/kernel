package kernel.http.server

object HttpServerProcessInspector {
    fun findListeningPid(port: Int): Long? {
        val process = ProcessBuilder(
            "lsof",
            "-tiTCP:$port",
            "-sTCP:LISTEN"
        ).start()

        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()

        return output.lineSequence()
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
            ?.toLongOrNull()
    }

    fun stopByPid(pid: Long): Boolean {
        val process = ProcessHandle.of(pid).orElse(null) ?: return false
        val stopped = process.destroy()
        if (!stopped) {
            process.destroyForcibly()
        }

        return true
    }
}
