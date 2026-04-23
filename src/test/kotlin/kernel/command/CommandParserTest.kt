package kernel.command

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class CommandParserTest {
    private val parser = CommandParser()
    private val workingDirectory = Paths.get("/tmp/kernel")

    @Test
    fun `parses command arguments and options`() {
        val input = parser.parse(
            arrayOf("make:migration", "create_users_table", "--create=users"),
            workingDirectory
        )

        assertEquals("make:migration", input.name)
        assertEquals(listOf("create_users_table"), input.arguments)
        assertEquals("users", input.option("create"))
        assertEquals(workingDirectory, input.workingDirectory)
    }
}
