package kernel.session

import kernel.foundation.Application
import kernel.foundation.ApplicationProcessLock
import kernel.foundation.ApplicationRuntime
import kernel.foundation.ProcessLockMode
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {
    @BeforeTest
    fun resetState() {
        ApplicationRuntime.resetForTests()
        ApplicationProcessLock.resetForTests()
        Session.resetForTests()
    }

    @Test
    fun `session persists encrypted values and reloads from disk`() {
        val application = Application.bootstrapRuntime(
            basePath = createTempDirectory("kernel-session-runtime-test").toAbsolutePath(),
            systemValues = emptyMap(),
            processLockMode = ProcessLockMode.OBSERVE
        )

        Session.put("locale", "es_MX")
        Session.put("counter", 7)
        Session.put("nullable", null)

        val sessionFile = application.path("storage/session/session.dat")
        assertTrue(sessionFile.exists())

        val rawContent = sessionFile.readBytes().toString(Charsets.UTF_8)
        assertFalse(rawContent.contains("es_MX"), rawContent)
        assertFalse(rawContent.contains("\"locale\""), rawContent)

        Session.resetForTests()
        Session.bootstrap(application)

        assertEquals("es_MX", Session.get<String>("locale"))
        assertEquals(7, Session.get<Int>("counter"))
        assertTrue(Session.has("nullable"))
        assertNull(Session.get<Any>("nullable"))
    }

    @Test
    fun `session exposes typed helpers and json export`() {
        val application = Application.bootstrap(
            basePath = createTempDirectory("kernel-session-api-test").toAbsolutePath(),
            systemValues = emptyMap(),
            processLockMode = ProcessLockMode.OBSERVE
        )

        Session.bootstrap(application)
        Session.put("locale", "en_US")
        Session.put("attempts", 3)

        assertEquals("en_US", Session.get<String>("locale"))
        assertEquals("fallback", Session.getOrDefault("missing", "fallback"))
        assertTrue(Session.has("attempts"))

        val json = Session.toJson()
        assertTrue(json.contains("\"locale\": \"en_US\""), json)
        assertTrue(json.contains("\"attempts\": 3"), json)

        Session.remove("attempts")
        assertFalse(Session.has("attempts"))

        Session.clear()
        assertTrue(Session.all().isEmpty())
    }

    @Test
    fun `session ignores driver failures without breaking runtime state`() {
        val application = Application.bootstrap(
            basePath = createTempDirectory("kernel-session-failure-test").toAbsolutePath(),
            systemValues = emptyMap(),
            processLockMode = ProcessLockMode.OBSERVE
        )

        Session.bootstrap(application, FailingSessionDriver())
        Session.put("locale", "fr_FR")

        assertEquals("fr_FR", Session.get<String>("locale"))
        assertTrue(Session.has("locale"))
    }
}

private class FailingSessionDriver : SessionDriver {
    override fun load(): Map<String, Any?> {
        error("load failed")
    }

    override fun save(values: Map<String, Any?>) {
        error("save failed")
    }
}
