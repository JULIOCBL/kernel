package kernel.routing

import kernel.database.orm.Model
import kernel.database.orm.ModelDefinition
import kernel.foundation.Application
import kernel.foundation.ApplicationRuntime
import kernel.http.DesktopResponse
import java.sql.ResultSet
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

    private class UserController {
        fun show(user: FakeUser): String = user.id.toString()
    }

    data class FakeUser(
        var id: Int
    ) : Model() {
        override fun primaryKeyValue(): Any? = id

        override fun persistenceAttributes(): Map<String, Any?> {
            return mapOf("id" to id)
        }

        companion object : ModelDefinition<FakeUser>(mapper = ::unreachableMapper) {
            override suspend fun find(id: Any?): FakeUser? {
                return FakeUser((id as String).toInt())
            }
        }
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

        val url = Route.route("dashboard.show", mapOf("id" to "42"))
        val redirectResponse = DesktopResponse.Redirect(target = Route.route("dashboard.show", mapOf("id" to "42")))

        assertEquals("desk:///dashboard/42", url)
        assertIs<DesktopResponse.Redirect>(redirectResponse)
        assertEquals("desk:///dashboard/42", redirectResponse.target)
    }

    @Test
    fun `facade supports prefix and middleware groups`() {
        ApplicationRuntime.resetForTests()
        val app = Application.bootstrap(createTempDirectory("kernel-routing-test")).initializeRuntime()
        val router = ApiRouter("api")
        app.config.set("services.routes.api.router", router)
        app.config.set("services.routes.controllers", ControllerRegistry())

        Route.withRouter(router) {
            Route.middleware("api")
                .prefix("v1")
                .group {
                    Route.get("health")
                        .name("health")
                        .action { _: Map<String, String> -> "ok" }

                    Route.prefix("tickets")
                        .middleware("auth")
                        .group {
                            Route.get("ping")
                                .action { _: Map<String, String> -> "pong" }
                        }
                }
        }

        val health = router.resolve("GET", "api://v1/health")
        val ticketPing = router.resolve("GET", "api://v1/tickets/ping")

        assertNotNull(health)
        assertEquals("ok", health.payload)
        assertEquals(listOf("api"), health.middleware)

        assertNotNull(ticketPing)
        assertEquals("pong", ticketPing.payload)
        assertEquals(listOf("api", "auth"), ticketPing.middleware)
        assertEquals("/v1/health", router.pathFor("health"))
    }

    @Test
    fun `facade supports grouped name prefixes`() {
        ApplicationRuntime.resetForTests()
        val app = Application.bootstrap(createTempDirectory("kernel-routing-test")).initializeRuntime()
        val router = ApiRouter("api")
        app.config.set("services.routes.api.router", router)
        app.config.set("services.routes.controllers", ControllerRegistry())

        Route.withRouter(router) {
            Route.name("api.")
                .prefix("v1")
                .group {
                    Route.name("users.")
                        .prefix("users")
                        .group {
                            Route.get("{id}")
                                .name("show")
                                .action { params -> params["id"] ?: "0" }
                        }
                }
        }

        assertEquals("/v1/users/42", router.pathFor("api.users.show", mapOf("id" to "42")))
        assertEquals("42", router.resolve("GET", "api://v1/users/42")?.payload)
    }

    @Test
    fun `facade supports implicit model binding from route params`() {
        ApplicationRuntime.resetForTests()
        val app = Application.bootstrap(createTempDirectory("kernel-routing-test")).initializeRuntime()
        val router = ApiRouter("api")
        app.config.set("services.routes.api.router", router)
        app.config.set("services.routes.controllers", ControllerRegistry())

        Route.withRouter(router) {
            Route.get("users/{user}", UserController::show)
        }

        val response = router.resolve("GET", "api://users/55")

        assertNotNull(response)
        assertEquals("55", response.payload)
    }

    companion object {
        private fun unreachableMapper(@Suppress("UNUSED_PARAMETER") resultSet: ResultSet): FakeUser {
            error("No deberia llamarse el mapper en esta prueba.")
        }
    }
}
