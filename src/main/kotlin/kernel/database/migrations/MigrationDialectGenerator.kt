package kernel.database.migrations

internal interface MigrationDialectGenerator {
    fun generateUp(migration: Migration): String

    fun generateDown(migration: Migration): String

    fun generateUpStatements(migration: Migration): List<String>

    fun generateDownStatements(migration: Migration): List<String>
}
