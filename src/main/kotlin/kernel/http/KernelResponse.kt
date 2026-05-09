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
