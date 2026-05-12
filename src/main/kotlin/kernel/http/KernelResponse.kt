package kernel.http

import kernel.routing.DesktopView

sealed class KernelResponse

data class ViewResponse(
    val view: DesktopView
) : KernelResponse()

data class JsonResponse(
    val payload: Any?,
    val status: Int = 200
) : KernelResponse()

data class TextResponse(
    val content: String,
    val status: Int = 200,
    val contentType: String = "text/plain; charset=utf-8"
) : KernelResponse()
