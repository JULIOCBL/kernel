package kernel.http

import kernel.foundation.Application
import kernel.routing.RouteResolution

/**
 * Representa una intencion de navegacion desktop.
 *
 * Mantiene el contexto minimo que un middleware necesita para decidir si deja
 * pasar la vista, redirige la navegacion o la bloquea.
 */
data class DesktopRequest(
    val app: Application,
    val routeName: String,
    val target: String,
    val params: Map<String, String> = emptyMap(),
    val resolution: RouteResolution? = null
)
