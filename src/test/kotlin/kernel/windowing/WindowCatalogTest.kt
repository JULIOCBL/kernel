package kernel.windowing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowCatalogTest {
    @Test
    fun `catalog lists and resolves window definitions`() {
        val catalog = WindowCatalog(
            listOf(
                WindowDefinition(
                    id = "example.form",
                    title = "Example Form",
                    widthDp = 900,
                    defaultProps = mapOf("mode" to "create")
                )
            )
        )

        assertTrue(catalog.contains("example.form"))
        assertFalse(catalog.contains("missing"))
        assertEquals(1, catalog.all().size)

        val descriptor = catalog.descriptorFor(
            definitionId = "example.form",
            instanceId = "abc",
            options = WindowLaunchOptions(
                props = mapOf("recordId" to "42"),
                title = "Custom Form"
            )
        )

        assertEquals("abc", descriptor.instanceId)
        assertEquals("example.form", descriptor.definitionId)
        assertEquals("Custom Form", descriptor.title)
        assertEquals(900, descriptor.widthDp)
        assertEquals("create", descriptor.props["mode"])
        assertEquals("42", descriptor.props["recordId"])
    }
}
