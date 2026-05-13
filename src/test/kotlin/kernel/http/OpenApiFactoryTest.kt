package kernel.http

import kernel.foundation.Application
import kernel.foundation.ApplicationRuntime
import kernel.routing.ApiRouter
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApiFactoryTest {
    class CreateProbeRequest(
        request: Request
    ) : FormRequest(request) {
        override fun rules(): Map<String, String> {
            return mapOf(
                "name" to "required|min:3",
                "email" to "required|email",
                "enabled" to "boolean"
            )
        }
    }

    @Test
    fun `build exports routes and request validation metadata`() {
        ApplicationRuntime.resetForTests()
        val app = Application.bootstrap(createTempDirectory("kernel-openapi-test"))
            .initializeRuntime()
            .loadConfig("app", mapOf("name" to "Kernel Test API", "version" to "1.2.3"))

        val router = ApiRouter("api")
        router.post(
            path = "probes/{probe}",
            action = { params -> params["probe"] ?: "0" },
            middleware = listOf("api", "auth"),
            name = "probes.store",
            requestType = CreateProbeRequest::class.java
        )

        val schema = OpenApiFactory.build(app, router)
        val info = schema["info"] as Map<*, *>
        val paths = schema["paths"] as Map<*, *>
        val probePath = paths["/probes/{probe}"] as Map<*, *>
        val post = probePath["post"] as Map<*, *>
        val requestBody = post["requestBody"] as Map<*, *>
        val content = requestBody["content"] as Map<*, *>
        val jsonContent = content["application/json"] as Map<*, *>
        val bodySchema = jsonContent["schema"] as Map<*, *>
        val properties = bodySchema["properties"] as Map<*, *>

        assertEquals("3.0.3", schema["openapi"])
        assertEquals("Kernel Test API", info["title"])
        assertEquals("1.2.3", info["version"])
        assertEquals("probes.store", post["operationId"])
        assertEquals(listOf("api", "auth"), post["middleware"])
        assertNotNull(post["parameters"])
        assertEquals(listOf("name", "email"), bodySchema["required"])

        val emailSchema = properties["email"] as Map<*, *>
        val enabledSchema = properties["enabled"] as Map<*, *>
        assertEquals("email", emailSchema["format"])
        assertEquals("boolean", enabledSchema["type"])

        val rules = post["x-validation-rules"] as Map<*, *>
        assertEquals("required|min:3", rules["name"])
        assertTrue(paths.containsKey("/probes/{probe}"))
    }
}
