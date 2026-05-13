package kernel.http

import kernel.foundation.Application
import kernel.lang.LangFile
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FormRequestTest {
    @Test
    fun `form request returns validation errors when payload is invalid`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "POST",
            target = "api://users",
            path = "/users",
            body = mapOf(
                "name" to "Al",
                "email" to "invalid-email"
            )
        )

        val error = assertFailsWith<ValidationException> {
            SampleCreateUserRequest(request).validateResolved()
        }

        assertEquals(
            listOf("El campo `name` debe tener al menos 3 caracteres."),
            error.errors["name"]
        )
        assertEquals(
            listOf("El campo `email` debe ser un email valido."),
            error.errors["email"]
        )
    }

    @Test
    fun `form request exposes typed validated payload based on rules and casts`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "POST",
            target = "api://flags",
            path = "/flags",
            body = mapOf(
                "age" to "34",
                "active" to "true",
                "amount" to "19.75"
            )
        )

        val formRequest = SampleTypedRequest(request)
        formRequest.validateResolved()
        val validated = formRequest.validatedTyped()

        assertIs<Int>(validated.getValue("age"))
        assertEquals(34, validated.int("age"))
        assertIs<Boolean>(validated.getValue("active"))
        assertEquals(true, validated.boolean("active"))
        assertIs<Double>(validated.getValue("amount"))
        assertEquals(19.75, validated.double("amount"))
    }

    @Test
    fun `form request validates uploaded files by extension and size`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "POST",
            target = "api://uploads/avatar",
            path = "/uploads/avatar",
            files = mapOf(
                "avatar" to UploadedFile(
                    field = "avatar",
                    originalName = "avatar.gif",
                    contentType = "image/gif",
                    bytes = ByteArray(10)
                )
            )
        )

        val error = assertFailsWith<ValidationException> {
            SampleUploadRequest(request).validateResolved()
        }

        assertEquals(
            listOf("El archivo `avatar` debe tener una extension valida: png, jpg."),
            error.errors["avatar"]
        )
    }

    @Test
    fun `form request supports prepare hooks safe payload and custom messages`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "POST",
            target = "api://users",
            path = "/users",
            body = mapOf(
                "name" to "  ada  ",
                "active" to "yes",
                "role" to "SUPERADMIN"
            )
        )

        val formRequest = SampleLifecycleRequest(request)
        formRequest.validateResolved()

        assertEquals("ADA", formRequest.validated().string("name"))
        assertEquals(false, formRequest.validated().has("role"))
        assertEquals(true, formRequest.validatedTyped().boolean("active"))
        assertEquals(mapOf("name" to "ADA"), formRequest.safe().only("name"))
        assertEquals(false, formRequest.safe().has("role"))
        assertTrue(formRequest.hookTriggered)
    }

    @Test
    fun `form request uses custom attribute names and messages`() {
        val request = Request(
            app = Application(Paths.get(".")),
            method = "POST",
            target = "api://users",
            path = "/users",
            body = mapOf("name" to "")
        )

        val error = assertFailsWith<ValidationException> {
            SampleCustomMessageRequest(request).validateResolved()
        }

        assertEquals(
            listOf("Debes capturar el nombre completo del usuario."),
            error.errors["name"]
        )
    }

    @Test
    fun `form request resolves default validation messages from lang store`() {
        val app = Application(Paths.get(".")).apply {
            loadLang(SampleValidationEn)
            config.set("app.locale", "en")
            config.set("app.fallback_locale", "en")
        }
        val request = Request(
            app = app,
            method = "POST",
            target = "api://users",
            path = "/users",
            body = mapOf("name" to "")
        )

        val error = assertFailsWith<ValidationException> {
            SampleCreateUserRequest(request).validateResolved()
        }

        assertEquals(
            listOf(
                "The name field is required.",
                "The name field must be at least 3 characters."
            ),
            error.errors["name"]
        )
    }
}

private class SampleCreateUserRequest(
    request: Request
) : FormRequest(request) {
    override fun rules(): Map<String, String> {
        return mapOf(
            "name" to "required|min:3",
            "email" to "required|email"
        )
    }
}

private class SampleTypedRequest(
    request: Request
) : FormRequest(request) {
    override fun rules(): Map<String, String> {
        return mapOf(
            "age" to "required|integer",
            "active" to "required|boolean",
            "amount" to "required|numeric"
        )
    }
}

private class SampleUploadRequest(
    request: Request
) : FormRequest(request) {
    override fun rules(): Map<String, String> {
        return mapOf(
            "avatar" to "file|extensions:png,jpg|max_file:5"
        )
    }
}

private class SampleLifecycleRequest(
    request: Request
) : FormRequest(request) {
    var hookTriggered: Boolean = false

    override fun prepareForValidation() {
        merge(
            mapOf(
                "name" to input("name").orEmpty().trim().uppercase(),
                "active" to input("active").orEmpty().trim().lowercase()
            )
        )
    }

    override fun passedValidation() {
        hookTriggered = true
    }

    override fun rules(): Map<String, String> {
        return mapOf(
            "name" to "required|min:3",
            "active" to "required|boolean"
        )
    }
}

private class SampleCustomMessageRequest(
    request: Request
) : FormRequest(request) {
    override fun rules(): Map<String, String> {
        return mapOf(
            "name" to "required"
        )
    }

    override fun validationAttributes(): Map<String, String> {
        return mapOf("name" to "nombre completo del usuario")
    }

    override fun messages(): Map<String, String> {
        return mapOf(
            "name.required" to "Debes capturar el nombre completo del usuario."
        )
    }
}

private object SampleValidationEn : LangFile {
    override val locale: String = "en"
    override val namespace: String = "validation"

    override fun load(): Map<String, Any?> {
        return mapOf(
            "required" to "The :attribute field is required.",
            "min" to "The :attribute field must be at least :min characters.",
            "attributes" to mapOf("name" to "name")
        )
    }
}
