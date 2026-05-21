package kernel.multisurface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceCatalogTest {
    @Test
    fun `lists definitions and resolves launch plans`() {
        val catalog = SurfaceCatalog(
            listOf(
                SurfaceDefinition(
                    id = "customer.display",
                    role = SurfaceRole.SECONDARY,
                    title = "Customer Display",
                    targetDisplayIndex = 1,
                    fullscreen = true,
                    externalDisplayPreferred = true,
                    capabilities = SurfaceCapabilities(
                        interactionMode = SurfaceInteractionMode.PASSIVE
                    ),
                    defaultProjection = SurfaceProjection(viewId = "home")
                )
            )
        )

        assertTrue(catalog.contains("customer.display"))
        assertEquals(1, catalog.all().size)

        val plan = catalog.launchPlanFor("customer.display")
        assertEquals("customer.display", plan.definitionId)
        assertEquals(1, plan.descriptor.targetDisplayIndex)
        assertTrue(plan.descriptor.fullscreen)
        assertTrue(plan.descriptor.externalDisplayPreferred)
        assertEquals("home", plan.initialProjection.viewId)
    }

    @Test
    fun `launch plan applies overrides without losing defaults`() {
        val catalog = SurfaceCatalog(
            listOf(
                SurfaceDefinition(
                    id = "secondary",
                    role = SurfaceRole.SECONDARY,
                    title = "Aux",
                    targetDisplayIndex = 1,
                    fullscreen = false,
                    externalDisplayPreferred = true,
                    capabilities = SurfaceCapabilities(
                        interactionMode = SurfaceInteractionMode.PASSIVE
                    ),
                    defaultProjection = SurfaceProjection(viewId = "overview")
                )
            )
        )

        val plan = catalog.launchPlanFor(
            definitionId = "secondary",
            options = SurfaceLaunchOptions(
                fullscreen = true,
                initialProjection = SurfaceProjection(viewId = "touch")
            )
        )

        assertEquals("Aux", plan.descriptor.title)
        assertEquals(1, plan.descriptor.targetDisplayIndex)
        assertTrue(plan.descriptor.fullscreen)
        assertTrue(plan.descriptor.externalDisplayPreferred)
        assertFalse(plan.descriptor.capabilities.isInteractive())
        assertEquals("touch", plan.initialProjection.viewId)
    }
}
