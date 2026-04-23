package kernel.database.migrations

import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal fun assertGeneratedSql(expected: String, actual: String) {
    assertEquals(expected.trimIndent(), actual)
}

internal fun assertGeneratedStatements(
    expected: List<String>,
    actual: List<String>
) {
    assertEquals(expected.map { sql -> sql.trimIndent() }, actual)
}

internal fun assertOptimizedSql(statements: List<String>) {
    assertTrue(statements.isNotEmpty(), "La migracion debe generar al menos una sentencia.")

    for (statement in statements) {
        assertEquals(statement.trim(), statement, "La sentencia no debe tener espacios externos.")
        assertTrue(statement.endsWith(";"), "La sentencia debe terminar con punto y coma: $statement")
        assertTrue(
            "\n\n\n" !in statement,
            "La sentencia no debe contener bloques de lineas en blanco innecesarios: $statement"
        )
    }
}
