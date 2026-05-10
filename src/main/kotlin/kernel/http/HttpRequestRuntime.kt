package kernel.http

object HttpRequestRuntime {
    private val current = ThreadLocal<Request?>()

    fun current(): Request {
        return current.get()
            ?: error("No existe una Request HTTP activa en el runtime actual.")
    }

    fun <T> withRequest(request: Request, block: () -> T): T {
        val previous = current.get()
        current.set(request)
        return try {
            block()
        } finally {
            current.set(previous)
        }
    }
}
