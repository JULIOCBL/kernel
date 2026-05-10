package kernel.foundation

import kernel.http.JsonResponse
import kernel.http.HttpMiddleware
import kernel.http.Request
import kernel.routing.ApiRouter
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpKernelTest {
    @Test
    fun `http kernel dispatches post routes through the api router`() {
        val app = Application(Paths.get("."))
        val router = ApiRouter("api")
        val kernel = HttpKernel(app)

        router.post("users", { params ->
            JsonResponse(
                payload = mapOf(
                    "name" to params["name"],
                    "email" to params["email"]
                ),
                status = 201
            )
        })

        val response = kernel.handle(
            request = Request(
                app = app,
                method = "POST",
                target = "api://users",
                path = "/users",
                body = mapOf(
                    "name" to "Ada",
                    "email" to "ada@example.com"
                )
            ),
            router = router
        ) as JsonResponse

        assertEquals(201, response.status)
        assertEquals(
            mapOf(
                "name" to "Ada",
                "email" to "ada@example.com"
            ),
            response.payload
        )
    }

    @Test
    fun `http kernel applies middleware aliases in configured priority order`() {
        val app = Application(Paths.get("."))
        val router = ApiRouter("api")
        val events = mutableListOf<String>()
        val kernel = object : HttpKernel(app) {
            override val middlewareGroups: Map<String, List<String>> = mapOf(
                "api" to listOf("logging", "auth")
            )
            override val routeMiddleware: Map<String, HttpMiddlewareFactory> = mapOf(
                "auth" to { _ ->
                    HttpMiddleware { request, next ->
                        events += "auth:before"
                        val response = next(request)
                        events += "auth:after"
                        response
                    }
                },
                "logging" to { _ ->
                    HttpMiddleware { request, next ->
                        events += "logging:before"
                        val response = next(request)
                        events += "logging:after"
                        response
                    }
                }
            )
            override val middlewarePriority: List<String> = listOf("auth", "logging")
        }

        router.get(
            path = "secure",
            action = { JsonResponse(mapOf("status" to "ok"), 200) },
            middleware = listOf("api")
        )

        val response = kernel.handle(
            request = Request(
                app = app,
                method = "GET",
                target = "api://secure",
                path = "/secure"
            ),
            router = router
        ) as JsonResponse

        assertEquals(200, response.status)
        assertEquals(
            listOf("auth:before", "logging:before", "logging:after", "auth:after"),
            events
        )
    }
}
