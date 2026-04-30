package kernel.routing

import kernel.foundation.Application
import kernel.foundation.ApplicationRuntime
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RouteFacadeTest {
    private class DashboardController {
        fun index(): String = "dashboard"
        fun sale(params: Map<String, String>): String = params["id"] ?: "0"
    }

    @Test
    fun `desktop router resolves registered facade routes`() {
        ApplicationRuntime.resetForTests()
        val app = Application.bootstrap(createTempDirectory("kernel-routing-test")).initializeRuntime()
        val router = DesktopRouter("desk")
        app.config.set("services.routes.desktop.router", router)
        app.config.set("services.routes.controllers", ControllerRegistry())

        Route.withRouter(router) {
            Route.get("dashboard", DashboardController::index)
            Route.get("docs/{id}", DashboardController::sale)
        }

        val dashboard = router.resolve("desk://dashboard")
        val detail = router.resolve("desk://docs/42?tab=summary")
        val wrongScheme = router.resolve("api://dashboard")

        assertNotNull(dashboard)
        assertEquals("dashboard", dashboard.path)
        assertEquals("dashboard", dashboard.payload)

        assertNotNull(detail)
        assertEquals("docs/42", detail.path)
        assertEquals("42", detail.params["id"])
        assertEquals("summary", detail.params["tab"])
        assertEquals("42", detail.payload)

        assertNull(wrongScheme)
    }
}
