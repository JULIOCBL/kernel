package kernel.routing

import kernel.http.ViewResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DefaultDesktopViewDispatcherTest {
    @Test
    fun `dispatch unwraps view response into desktop view`() {
        val expectedView = DesktopView(
            name = "sale_success",
            title = "Venta exitosa",
            model = mapOf("message" to "ok")
        )
        val resolution = RouteResolution(
            scheme = "kernelplayground",
            path = "tickets/demo-sale",
            params = emptyMap(),
            payload = ViewResponse(expectedView)
        )

        val dispatched = DefaultDesktopViewDispatcher.dispatch(resolution)

        assertNotNull(dispatched)
        assertEquals(expectedView, dispatched)
    }
}
