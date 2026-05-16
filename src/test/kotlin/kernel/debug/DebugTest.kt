package kernel.debug

import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugTest {
    @Test
    fun `dump formats structured values`() {
        var printed = ""

        Debug.dump(
            mapOf(
                "name" to "Kernel",
                "features" to listOf("cli", "migrations")
            )
        ) { output ->
            printed = output
        }

        assertContains(printed, "dump = {")
        assertContains(printed, "\"name\": \"Kernel\"")
        assertContains(printed, "\"features\": [")
        assertContains(printed, "\"cli\"")
    }

    @Test
    fun `dd prints and throws stop signal`() {
        var printed = ""

        val signal = assertFailsWith<DumpAndDieSignal> {
            Debug.dd("halt") { output ->
                printed = output
            }
        }

        assertEquals(DumpAndDieSignal.instance, signal)
        assertContains(printed, "dd = \"halt\"")
    }

    @Test
    fun `dump preserves multiline strings as readable blocks`() {
        var printed = ""
        val json = """
            {
              "device_name": "MacBook-Pro-de-Julio.local",
              "operating_system": "Mac OS X"
            }
        """.trimIndent()

        Debug.dump(json) { output ->
            printed = output
        }

        assertContains(printed, "dump = \"\"\"")
        assertContains(printed, "\"device_name\": \"MacBook-Pro-de-Julio.local\"")
        assertContains(printed, "\"operating_system\": \"Mac OS X\"")
        assertFalse(printed.contains("\\n"), printed)
    }

    @Test
    fun `debug object supports explicit api`() {
        var printed = ""

        Debug.dump(IllegalStateException("boom")) { output ->
            printed = output
        }

        assertContains(printed, "IllegalStateException {")
        assertContains(printed, "message: \"boom\"")
    }

    @Test
    fun `dump renders object properties for custom classes`() {
        var printed = ""

        Debug.dump(
            DebugUser(
                id = 1,
                name = "Julio",
                profile = DebugProfile(active = true)
            )
        ) { output ->
            printed = output
        }

        assertContains(printed, "DebugUser {")
        assertContains(printed, "id: 1")
        assertContains(printed, "name: \"Julio\"")
        assertContains(printed, "profile: DebugProfile {")
        assertContains(printed, "active: true")
    }

    @Test
    fun `dump marks recursion for self references`() {
        var printed = ""
        val node = DebugNode("root")
        node.next = node

        Debug.dump(node) { output ->
            printed = output
        }

        assertContains(printed, "DebugNode {")
        assertContains(printed, "name: \"root\"")
        assertContains(printed, "next: DebugNode {<recursion>}")
    }

    @Test
    fun `dump truncates large structures safely`() {
        var printed = ""

        withDebugConfig(
            maxCollectionItems = 25,
            maxStringLength = 500
        ) {
            Debug.dump(
                mapOf(
                    "text" to "a".repeat(700),
                    "items" to (1..40).toList()
                )
            ) { output ->
                printed = output
            }
        }

        assertContains(printed, "(truncated, length=700)")
        assertContains(printed, "... +15 more")
    }

    @Test
    fun `dump limits nested depth`() {
        var printed = ""

        withDebugConfig(maxDepth = 6) {
            Debug.dump(
                DeepNode(
                    1,
                    DeepNode(
                        2,
                        DeepNode(
                            3,
                            DeepNode(
                                4,
                                DeepNode(
                                    5,
                                    DeepNode(
                                        6,
                                        DeepNode(7, null)
                                    )
                                )
                            )
                        )
                    )
                )
            ) { output ->
                printed = output
            }
        }

        assertContains(printed, "DeepNode {<max-depth>}")
    }

    @Test
    fun `dump supports custom debug renderers`() {
        var printed = ""

        Debug.dump(DebugBox("secret")) { output ->
            printed = output
        }

        assertContains(printed, "DebugPayload {")
        assertContains(printed, "payload: \"SECRET\"")
        assertFalse(printed.contains("secret\""), printed)
    }

    @Test
    fun `dump handles optional pair and triple`() {
        var printed = ""

        Debug.dump(
            mapOf(
                "optional" to Optional.of("kernel"),
                "pair" to ("alpha" to 2),
                "triple" to Triple("beta", true, 3)
            )
        ) { output ->
            printed = output
        }

        assertContains(printed, "Optional(\"kernel\")")
        assertContains(printed, "Pair {")
        assertContains(printed, "first: \"alpha\"")
        assertContains(printed, "Triple {")
        assertContains(printed, "third: 3")
    }

    @Test
    fun `dump can disable truncation limits`() {
        var printed = ""

        withDebugConfig(
            maxCollectionItems = null,
            maxStringLength = null
        ) {
            Debug.dump(
                mapOf(
                    "text" to "a".repeat(700),
                    "items" to (1..40).toList()
                )
            ) { output ->
                printed = output
            }
        }

        assertFalse(printed.contains("(truncated, length=700)"), printed)
        assertFalse(printed.contains("... +"), printed)
        assertContains(printed, "\"${"a".repeat(700)}\"")
        assertContains(printed, "40")
    }
}

private inline fun withDebugConfig(
    maxDepth: Int? = DebugConfig.maxDepth,
    maxCollectionItems: Int? = DebugConfig.maxCollectionItems,
    maxObjectFields: Int? = DebugConfig.maxObjectFields,
    maxStringLength: Int? = DebugConfig.maxStringLength,
    maxStackFrames: Int? = DebugConfig.maxStackFrames,
    block: () -> Unit
) {
    val previousMaxDepth = DebugConfig.maxDepth
    val previousMaxCollectionItems = DebugConfig.maxCollectionItems
    val previousMaxObjectFields = DebugConfig.maxObjectFields
    val previousMaxStringLength = DebugConfig.maxStringLength
    val previousMaxStackFrames = DebugConfig.maxStackFrames

    try {
        DebugConfig.maxDepth = maxDepth
        DebugConfig.maxCollectionItems = maxCollectionItems
        DebugConfig.maxObjectFields = maxObjectFields
        DebugConfig.maxStringLength = maxStringLength
        DebugConfig.maxStackFrames = maxStackFrames
        block()
    } finally {
        DebugConfig.maxDepth = previousMaxDepth
        DebugConfig.maxCollectionItems = previousMaxCollectionItems
        DebugConfig.maxObjectFields = previousMaxObjectFields
        DebugConfig.maxStringLength = previousMaxStringLength
        DebugConfig.maxStackFrames = previousMaxStackFrames
    }
}

private data class DebugUser(
    val id: Int,
    val name: String,
    val profile: DebugProfile
)

private data class DebugProfile(
    val active: Boolean
)

private class DebugNode(
    val name: String
) {
    var next: DebugNode? = null
}

private data class DeepNode(
    val level: Int,
    val next: DeepNode?
)

private class DebugBox(
    private val secret: String
) : DebugRenderable {
    override fun toDebugValue(): Any {
        return DebugPayload(secret.uppercase())
    }
}

private data class DebugPayload(
    val payload: String
)
