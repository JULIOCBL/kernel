package kernel.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `scheme router prioritizes static paths over dynamic params`() {
        val router = SchemeRouter("api")

        router.get("users/new", { "new" })
        router.get("users/{id}", { params -> params["id"] ?: "" })

        assertEquals("new", router.resolve("GET", "api://users/new")?.payload)
        assertEquals("77", router.resolve("GET", "api://users/77")?.payload)
    }

    @Test
    fun `scheme router supports terminal wildcards`() {
        val router = SchemeRouter("api")

        router.get("files/*path", { params -> params["path"] ?: "" })

        val resolution = router.resolve("GET", "api://files/images/icons/logo.svg?disk=public")

        assertNotNull(resolution)
        assertEquals("images/icons/logo.svg", resolution.params["path"])
        assertEquals("public", resolution.params["disk"])
        assertEquals("images/icons/logo.svg", resolution.payload)
    }

    @Test
    fun `scheme router rejects ambiguous dynamic route signatures`() {
        val router = SchemeRouter("api")

        router.get("users/{id}", { params -> params["id"] ?: "" })
        router.get("users/{slug}", { params -> params["slug"] ?: "" })

        assertFailsWith<IllegalArgumentException> {
            router.routes()
        }
    }

    @Test
    fun `scheme router can be frozen after compilation`() {
        val router = SchemeRouter("api")

        router.get("health", { "ok" })
        router.freeze()

        assertTrue(router.isFrozen())
        assertEquals("ok", router.resolve("GET", "api://health")?.payload)
    }

    @Test
    fun `scheme router rejects new registrations after freeze`() {
        val router = SchemeRouter("api")

        router.get("health", { "ok" })
        router.freeze()

        assertFailsWith<IllegalStateException> {
            router.get("users", { "users" })
        }
    }
}
