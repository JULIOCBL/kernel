package kernel.http

fun interface Middleware {
    fun handle(
        request: DesktopRequest,
        next: (DesktopRequest) -> DesktopResponse
    ): DesktopResponse
}
