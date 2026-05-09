package kernel.routing

import kernel.http.ViewResponse

/**
 * Dispatcher por defecto del kernel para payloads comunes.
 */
object DefaultDesktopViewDispatcher : DesktopViewDispatcher {
    override fun dispatch(resolution: RouteResolution): DesktopView? {
        return when (val payload = resolution.payload) {
            null -> null
            is DesktopView -> payload
            is ViewResponse -> payload.view
            is Map<*, *> -> mapPayloadToView(payload)
            else -> DesktopView(
                name = resolution.path,
                title = resolution.path,
                model = mapOf("payload" to payload)
            )
        }
    }

    private fun mapPayloadToView(payload: Map<*, *>): DesktopView {
        val typedPayload = payload.entries
            .filter { (key, _) -> key != null }
            .associate { (key, value) -> key.toString() to value }

        val name = typedPayload["screen"]?.toString()
            ?: typedPayload["name"]?.toString()
            ?: "desktop.view"
        val title = typedPayload["title"]?.toString() ?: name
        val model = typedPayload - setOf("screen", "name", "title")

        return DesktopView(
            name = name,
            title = title,
            model = model
        )
    }
}
