package kernel.windowing

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultWindowManagerTest {
    @Test
    fun `window manager opens windows from catalog definitions`() {
        val manager = DefaultWindowManager(
            catalog = WindowCatalog(
                listOf(
                    WindowDefinition(
                        id = "example.detail",
                        title = "Example Detail",
                        heightDp = 540
                    )
                )
            )
        )

        val instanceId = manager.open(
            definitionId = "example.detail",
            options = WindowLaunchOptions(
                props = mapOf("recordId" to "99")
            )
        )

        val descriptor = manager.windows().value.single()

        assertEquals(instanceId, descriptor.instanceId)
        assertEquals("example.detail", descriptor.definitionId)
        assertEquals(540, descriptor.heightDp)
        assertEquals("99", descriptor.props["recordId"])
    }
}
