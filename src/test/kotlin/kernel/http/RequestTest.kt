package kernel.http

import kernel.foundation.Application
import java.nio.file.Paths
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestTest {
    @Test
    fun `request supports laravel-like input helpers`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "POST",
            target = "api://users/5",
            path = "/users/5",
            queryParams = mapOf("page" to "2"),
            body = mapOf("name" to "Ada", "active" to "true", "tags" to "a, b, c"),
            routeParams = mapOf("user" to "5"),
            headers = mapOf("Accept" to "application/json", "Host" to "localhost:8080"),
            remoteAddress = "127.0.0.1"
        )

        assertEquals("Ada", request.string("name"))
        assertEquals(2, request.int("page"))
        assertEquals(true, request.boolean("active"))
        assertEquals(listOf("a", "b", "c"), request.array("tags"))
        assertTrue(request.has("name"))
        assertTrue(request.hasAny(listOf("missing", "name")))
        assertTrue(request.filled("name"))
        assertTrue(request.isNotFilled("missing"))
        assertTrue(request.missing("missing"))
        assertEquals("5", request.route("user"))
        assertEquals("localhost:8080", request.host())
        assertEquals("127.0.0.1", request.ip())
        assertTrue(request.expectsJson())
        assertEquals("POST", request.method())
        assertTrue(request.isMethod("post"))
        assertEquals(mapOf("name" to "Ada"), request.only("name"))
        assertFalse("active" in request.except("active"))
        assertEquals(listOf("user", "page", "name", "active", "tags"), request.keys())
    }

    @Test
    fun `request merge and merge if missing behave predictably`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "POST",
            target = "api://users",
            path = "/users",
            body = mapOf("name" to "Ada")
        )

        request.merge(mapOf("role" to "admin"))
        request.mergeIfMissing(mapOf("name" to "Grace", "email" to "ada@example.com"))

        assertEquals("Ada", request.input("name"))
        assertEquals("admin", request.input("role"))
        assertEquals("ada@example.com", request.input("email"))
    }

    @Test
    fun `request exposes query post json url and segment helpers`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "PUT",
            target = "api://users/42?filter=active&page=2",
            path = "/users/42",
            queryParams = mapOf("filter" to "active", "page" to "2"),
            body = mapOf("name" to "Ada"),
            headers = mapOf("Host" to "127.0.0.1:8080")
        )

        assertEquals(mapOf("filter" to "active", "page" to "2"), request.query())
        assertEquals("active", request.query("filter"))
        assertEquals(mapOf("name" to "Ada"), request.post())
        assertEquals("Ada", request.json("name"))
        assertEquals("/users/42", request.path)
        assertEquals("users/42", request.path())
        assertEquals(listOf("users", "42"), request.segments())
        assertEquals("users", request.segment(1))
        assertEquals("42", request.segment(2))
        assertNull(request.segment(3))
        assertEquals("http://127.0.0.1:8080/users/42", request.url())
        assertEquals("http://127.0.0.1:8080/users/42?filter=active&page=2", request.fullUrl())
        assertEquals(
            "http://127.0.0.1:8080/users/42?filter=active&page=3&lang=es",
            request.fullUrlWithQuery(mapOf("page" to "3", "lang" to "es"))
        )
        assertEquals(
            "http://127.0.0.1:8080/users/42?page=2",
            request.fullUrlWithoutQuery("filter")
        )
    }

    @Test
    fun `request supports accepts bearer token and conditional callbacks`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "GET",
            target = "api://profile",
            path = "/profile",
            body = mapOf("name" to "Ada"),
            headers = mapOf(
                "Accept" to "application/json, text/plain;q=0.9",
                "Authorization" to "Bearer abc123"
            )
        )

        var whenHasValue = ""
        var whenFilledValue = ""
        var missingTriggered = false

        request.whenHas("name", { whenHasValue = it })
        request.whenFilled("name", { whenFilledValue = it })
        request.whenMissing("email", { missingTriggered = true })

        assertTrue(request.accepts("application/json"))
        assertTrue(request.acceptsJson())
        assertTrue(request.wantsJson())
        assertEquals("application/json", request.prefers(listOf("text/html", "application/json")))
        assertEquals("abc123", request.bearerToken())
        assertEquals("Ada", whenHasValue)
        assertEquals("Ada", whenFilledValue)
        assertTrue(missingTriggered)
    }

    @Test
    fun `request exposes locale from attributes or app config`() {
        val app = Application(Paths.get(".")).apply {
            config.set("app.locale", "es")
        }
        val request = Request(
            app = app,
            method = "GET",
            target = "api://profile",
            path = "/profile"
        )

        assertEquals("es", request.locale())
        request.setLocale("en")
        assertEquals("en", request.locale())
    }

    @Test
    fun `uploaded file can be stored using the request storage root`() {
        val root = createTempDirectory("request-upload-storage")
        val uploadedFile = UploadedFile(
            field = "avatar",
            originalName = "avatar.png",
            contentType = "image/png",
            bytes = "png-bytes".toByteArray(),
            storageRoot = root
        )

        val relativePath = uploadedFile.store("avatars")

        assertTrue(root.resolve(relativePath).toFile().exists())
    }
}
