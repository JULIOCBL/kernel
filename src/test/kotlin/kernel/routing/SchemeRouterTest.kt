package kernel.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemeRouterTest {
    @Test
    fun `scheme router resolves multiple http methods for the same static path`() {
        val router = SchemeRouter("api")

        router.get("health", { "get" })
        router.post("health", { "post" })
        router.patch("health", { "patch" })
        router.options("health", { "options" })
        router.trace("health", { "trace" })
        router.connect("health", { "connect" })
        router.head("health", { "head" })
        router.delete("health", { "delete" })
        router.put("health", { "put" })

        assertEquals("get", router.resolve("GET", "api://health")?.payload)
        assertEquals("post", router.resolve("POST", "api://health")?.payload)
        assertEquals("patch", router.resolve("PATCH", "api://health")?.payload)
        assertEquals("options", router.resolve("OPTIONS", "api://health")?.payload)
        assertEquals("trace", router.resolve("TRACE", "api://health")?.payload)
        assertEquals("connect", router.resolve("CONNECT", "api://health")?.payload)
        assertEquals("head", router.resolve("HEAD", "api://health")?.payload)
        assertEquals("delete", router.resolve("DELETE", "api://health")?.payload)
        assertEquals("put", router.resolve("PUT", "api://health")?.payload)
        assertNull(router.resolve("POST", "desk://health"))
    }

    @Test
    fun `scheme router resolves custom methods and dynamic params`() {
        val router = SchemeRouter("api")

        router.method("PURGE", "cache/{key}", { params ->
            params["key"] ?: ""
        })

        val resolution = router.resolve("PURGE", "api://cache/users?scope=all")

        assertNotNull(resolution)
        assertEquals("cache/users", resolution.path)
        assertEquals("users", resolution.params["key"])
        assertEquals("all", resolution.params["scope"])
        assertEquals("users", resolution.payload)
    }

    @Test
    fun `scheme router exposes registered route definitions`() {
        val router = SchemeRouter("desk")

        router.get("/", { "home" }, middleware = listOf("desktop"), name = "home")
        router.post("users", { "store" }, middleware = listOf("api"), name = "users.store")

        val routes = router.routes()

        assertEquals(2, routes.size)
        assertEquals("GET", routes[0].method)
        assertEquals("", routes[0].path)
        assertEquals("home", routes[0].name)
        assertEquals(listOf("desktop"), routes[0].middleware)
        assertEquals("POST", routes[1].method)
        assertEquals("users", routes[1].path)
        assertEquals("users.store", routes[1].name)
        assertTrue("api" in routes[1].middleware)
    }
}
