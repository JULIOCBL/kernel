package kernel.routing

import kernel.foundation.Application
import kernel.foundation.DesktopKernel
import kernel.http.DesktopRequest
import kernel.http.DesktopResponse
import kernel.http.Middleware
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopNavigatorTest {
    @Test
    fun `desktop middleware runs before route action is materialized`() {
        val application = Application.bootstrap(createTempDirectory("desktop-navigator-test"))
        val router = DesktopRouter("desk")
        val links = LinkGenerator("desk")
        val routeState = RouteStateStore<RouteResolution>()
        val viewState = RouteStateStore<DesktopView>()
        var actionExecuted = false

        router.get(
            path = "dashboard",
            middleware = listOf("auth"),
            action = {
                actionExecuted = true
                DesktopView(name = "dashboard", title = "Dashboard")
            }
        )

        val kernel = object : DesktopKernel(application) {
            override val routeMiddleware: Map<String, (Application) -> Middleware> = mapOf(
                "auth" to {
                    Middleware { _, _ ->
                        DesktopResponse.Redirect("/activation")
                    }
                }
            )
        }

        val navigator = DesktopNavigator(
            app = application,
            links = links,
            router = router,
            desktopKernel = kernel,
            routeState = routeState,
            viewState = viewState,
            viewDispatcher = DefaultDesktopViewDispatcher,
            onNavigation = { _, _, _, _ -> }
        )

        val resolved = navigator.navigate("/dashboard")

        assertNull(resolved)
        assertEquals(false, actionExecuted)
        assertEquals(DesktopResponse.Redirect("/activation"), application.config.get("runtime.last_navigation_response"))
    }
}
