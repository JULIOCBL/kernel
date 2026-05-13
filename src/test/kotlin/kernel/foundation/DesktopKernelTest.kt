package kernel.foundation

import kernel.http.DesktopRequest
import kernel.http.DesktopResponse
import kernel.http.Middleware
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopKernelTest {
    @Test
    fun `desktop kernel runs middleware in declaration order`() {
        val events = mutableListOf<String>()
        val application = Application.bootstrap(createTempDirectory("desktop-kernel-test"))
        val kernel = object : DesktopKernel(application) {
            override val middleware = listOf<(Application) -> Middleware>(
                { _: Application ->
                    Middleware { request, next ->
                        events += "global-before"
                        val response = next(request)
                        events += "global-after"
                        response
                    }
                }
            )

            override val middlewareGroups = mapOf(
                "desktop" to listOf("audit")
            )

            override val routeMiddleware: Map<String, (Application) -> Middleware> = mapOf(
                "audit" to { _: Application ->
                    Middleware { request, next ->
                        events += "route-before"
                        val response = next(request)
                        events += "route-after"
                        response
                    }
                }
            )
        }

        val response = kernel.handle(
            request = DesktopRequest(
                app = application,
                routeName = "dashboard",
                target = "desk://dashboard"
            ),
            routeMiddlewareAliases = listOf("desktop")
        )

        assertIs<DesktopResponse.Success>(response)
        assertEquals(
            listOf("global-before", "route-before", "route-after", "global-after"),
            events
        )
    }

    @Test
    fun `desktop kernel short circuits when middleware redirects`() {
        val application = Application.bootstrap(createTempDirectory("desktop-kernel-test"))
        val kernel = object : DesktopKernel(application) {
            override val routeMiddleware: Map<String, (Application) -> Middleware> = mapOf(
                "auth" to { _: Application ->
                    Middleware { _, _ ->
                        DesktopResponse.Redirect("/activation")
                    }
                }
            )
        }

        val response = kernel.handle(
            request = DesktopRequest(
                app = application,
                routeName = "dashboard",
                target = "desk://dashboard"
            ),
            routeMiddlewareAliases = listOf("auth")
        )

        assertEquals(DesktopResponse.Redirect("/activation"), response)
    }
}
