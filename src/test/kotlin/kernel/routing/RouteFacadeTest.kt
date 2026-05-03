package kernel.routing

import kernel.foundation.Application
import kernel.foundation.ApplicationRuntime
import kernel.http.DesktopResponse
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    @Test
    fun `desktop router stores route middleware declared through fluent facade`() {
        ApplicationRuntime.resetForTests()
        val app = Application.bootstrap(createTempDirectory("kernel-routing-test")).initializeRuntime()
        val router = DesktopRouter("desk")
        app.config.set("services.routes.desktop.router", router)
        app.config.set("services.routes.controllers", ControllerRegistry())

        Route.withRouter(router) {
            Route.get("billing")
                .middleware("desktop", "auth", "enclave")
                .action { _: Map<String, String> -> "ok" }
        }

        val resolution = router.resolve("desk://billing")

        assertNotNull(resolution)
        assertEquals(listOf("desktop", "auth", "enclave"), resolution.middleware)
        assertEquals("ok", resolution.payload)
    }

    @Test
    fun `named desktop routes generate urls and redirect responses`() {
        ApplicationRuntime.resetForTests()
        val app = Application.bootstrap(createTempDirectory("kernel-routing-test")).initializeRuntime()
        val router = DesktopRouter("desk")
        app.config.set("services.routes.desktop.router", router)
        app.config.set("services.routes.desktop.links", LinkGenerator("desk"))
        app.config.set("services.routes.controllers", ControllerRegistry())

        Route.withRouter(router) {
            Route.get("dashboard/{id}")
                .name("dashboard.show")
                .action { params -> params["id"] ?: "0" }
        }

        val url = route("dashboard.show", mapOf("id" to "42"))
        val redirectResponse = redirect().route("dashboard.show", mapOf("id" to "42"))

        assertEquals("desk:///dashboard/42", url)
        assertIs<DesktopResponse.Redirect>(redirectResponse)
        assertEquals("desk:///dashboard/42", redirectResponse.target)
    }
}
