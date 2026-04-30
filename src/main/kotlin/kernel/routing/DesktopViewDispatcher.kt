package kernel.routing

/**
 * Traduce el payload de una ruta desktop al objeto de vista que consumirá la UI.
 */
fun interface DesktopViewDispatcher {
    fun dispatch(resolution: RouteResolution): DesktopView?
}
