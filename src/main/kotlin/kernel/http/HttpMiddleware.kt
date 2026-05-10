package kernel.http

fun interface HttpMiddleware {
    fun handle(
        request: Request,
        next: (Request) -> KernelResponse
    ): KernelResponse
}
