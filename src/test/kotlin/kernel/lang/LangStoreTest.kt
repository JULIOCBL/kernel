package kernel.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LangStoreTest {
    @Test
    fun `lang store resolves dot notation with replacements and fallback locale`() {
        val store = LangStore()
            .load(TestValidationEs)
            .load(TestAuthEn)

        assertEquals(
            "No pudimos autenticar a ada@example.com.",
            store.translate(
                key = "auth.failed",
                locale = "es-MX",
                replacements = mapOf("user" to "ada@example.com"),
                fallbackLocale = "en"
            )
        )
    }

    @Test
    fun `lang store exposes loaded locales and namespace keys`() {
        val store = LangStore().load(TestValidationEs)

        assertTrue(store.has("es", "validation.required"))
        assertEquals(
            "El campo :attribute es obligatorio.",
            store.get("es", "validation.required")
        )
        assertEquals(setOf("es"), store.locales())
    }
}

private object TestValidationEs : LangFile {
    override val locale: String = "es"
    override val namespace: String = "validation"

    override fun load(): Map<String, Any?> {
        return mapOf(
            "required" to "El campo :attribute es obligatorio."
        )
    }
}

private object TestAuthEn : LangFile {
    override val locale: String = "en"
    override val namespace: String = "auth"

    override fun load(): Map<String, Any?> {
        return mapOf(
            "failed" to "No pudimos autenticar a :user."
        )
    }
}
