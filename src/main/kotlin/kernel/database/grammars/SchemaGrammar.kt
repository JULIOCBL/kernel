package kernel.database.grammars

import kernel.database.migrations.Migration

/**
 * Contrato base de una gramatica de schema.
 *
 * Cada motor traduce la misma migracion del DSL canonico del kernel a su SQL
 * concreto, igual que Laravel separa `Blueprint` de `Grammar`.
 */
interface SchemaGrammar {
    fun generateUp(migration: Migration): String

    fun generateDown(migration: Migration): String

    fun generateUpStatements(migration: Migration): List<String>

    fun generateDownStatements(migration: Migration): List<String>
}
