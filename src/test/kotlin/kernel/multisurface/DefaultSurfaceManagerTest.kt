package kernel.multisurface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultSurfaceManagerTest {
    @Test
    fun `opens a surface from catalog preserving display defaults`() {
        val manager = DefaultSurfaceManager(
            catalog = SurfaceCatalog(
                listOf(
                    SurfaceDefinition(
                        id = "customer.display",
                        role = SurfaceRole.SECONDARY,
                        title = "Customer Display",
                        targetDisplayIndex = 1,
                        externalDisplayPreferred = true,
                        defaultProjection = SurfaceProjection(viewId = "home")
                    )
                )
            ),
            displayDetector = { 2 }
        )

        val descriptor = manager.open("customer.display")

        assertEquals(1, descriptor.targetDisplayIndex)
        assertTrue(descriptor.externalDisplayPreferred)
        assertEquals("home", manager.projection("customer.display").value.viewId)
    }

    @Test
    fun `opens surfaces and stores projections`() {
        val manager = DefaultSurfaceManager(displayDetector = { 2 })

        manager.openSurface(
            descriptor = SurfaceDescriptor(
                id = "secondary",
                role = SurfaceRole.SECONDARY,
                title = "Aux",
                capabilities = SurfaceCapabilities()
            ),
            initialProjection = SurfaceProjection(
                viewId = "ads",
                headline = "Promo"
            )
        )

        assertEquals(2, manager.displayCount())
        assertTrue(manager.isVisible("secondary"))
        assertEquals("ads", manager.projection("secondary").value.viewId)
        assertEquals("Promo", manager.projection("secondary").value.headline)
    }

    @Test
    fun `dispatches actions only for interactive surfaces`() {
        var dispatched = false
        val manager = DefaultSurfaceManager(
            displayDetector = { 1 },
            coordinator = SurfaceCoordinator { surfaceId, action ->
                dispatched = surfaceId == "touch" && action.type == "add-product"
                true
            }
        )

        manager.openSurface(
            descriptor = SurfaceDescriptor(
                id = "passive",
                role = SurfaceRole.SECONDARY,
                title = "Passive"
            )
        )
        manager.openSurface(
            descriptor = SurfaceDescriptor(
                id = "touch",
                role = SurfaceRole.SECONDARY,
                title = "Touch",
                capabilities = SurfaceCapabilities(
                    interactionMode = SurfaceInteractionMode.INTERACTIVE,
                    canDispatchActions = true
                )
            )
        )

        assertFalse(manager.dispatchAction("passive", BasicSurfaceAction("add-product")))
        assertTrue(manager.dispatchAction("touch", BasicSurfaceAction("add-product")))
        assertTrue(dispatched)
    }

    @Test
    fun `closing a surface removes descriptor and projection`() {
        val manager = DefaultSurfaceManager(displayDetector = { 1 })

        manager.openSurface(
            descriptor = SurfaceDescriptor(
                id = "secondary",
                role = SurfaceRole.SECONDARY,
                title = "Aux"
            ),
            initialProjection = SurfaceProjection(viewId = "overview")
        )

        manager.closeSurface("secondary")

        assertNull(manager.descriptor("secondary"))
        assertFalse(manager.isVisible("secondary"))
        assertEquals("", manager.projection("secondary").value.viewId)
    }
}
