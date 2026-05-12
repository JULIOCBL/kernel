package kernel.http

abstract class Controller {
    protected fun json(
        payload: Any?,
        status: Int = 200
    ): JsonResponse {
        return JsonResponse(payload = payload, status = status)
    }

    protected fun text(
        content: String,
        status: Int = 200,
        contentType: String = "text/plain; charset=UTF-8"
    ): TextResponse {
        return TextResponse(
            content = content,
            status = status,
            contentType = contentType
        )
    }
}
