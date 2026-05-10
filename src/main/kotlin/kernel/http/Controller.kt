package kernel.http

abstract class Controller {
    protected fun json(
        payload: Any?,
        status: Int = 200
    ): JsonResponse {
        return JsonResponse(payload = payload, status = status)
    }
}
