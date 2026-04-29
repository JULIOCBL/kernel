package kernel.database.grammars

import kernel.database.migrations.Migration

/**
 * Gramatica nativa de PostgreSQL para el DSL de migraciones.
 */
class PostgresGrammar : SchemaGrammar {
    override fun generateUp(migration: Migration): String {
        return generateUpStatements(migration).joinToString("\n\n")
    }

    override fun generateDown(migration: Migration): String {
        return generateDownStatements(migration).joinToString("\n\n")
    }

    /**
     * Genera un script SQL para aplicar la migracion.
     */
    override fun generateUpStatements(migration: Migration): List<String> {
        return migration.upSql().map(::normalizeStatement)
    }

    override fun generateDownStatements(migration: Migration): List<String> {
        return migration.downSql().map(::normalizeStatement)
    }

    private fun normalizeStatement(sql: String): String {
        val normalized = sql.trim()
        require(normalized.isNotEmpty()) {
            "No se puede traducir una sentencia SQL vacia a PostgreSQL."
        }

        val translated = TOKEN_REPLACEMENTS.fold(normalized) { current, replacement ->
            replacement.apply(current)
        }

        ensureNoUnsupportedFragments(translated)

        return translated
    }

    private fun ensureNoUnsupportedFragments(sql: String) {
        if (SUPPORTED_ENUM_STATEMENT_PREFIXES.any { prefix ->
                sql.startsWith(prefix, ignoreCase = true)
            }) {
            return
        }

        val unsupportedFragment = UNSUPPORTED_SQL_PATTERNS.firstOrNull { pattern ->
            pattern.containsMatchIn(sql)
        }

        require(unsupportedFragment == null) {
            "La sentencia no es compatible con PostgreSQL: `$sql`."
        }
    }

    private class RegexReplacement(
        pattern: String,
        private val replacement: (MatchResult) -> String
    ) {
        private val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE))

        fun apply(value: String): String {
            return regex.replace(value) { match -> replacement(match) }
        }
    }

    companion object {
        private val SUPPORTED_ENUM_STATEMENT_PREFIXES = listOf(
            "CREATE TYPE",
            "ALTER TYPE",
            "DROP TYPE"
        )

        private val TOKEN_REPLACEMENTS = listOf(
            RegexReplacement("""\bBIGINCREMENTS\b""") { "BIGSERIAL" },
            RegexReplacement("""\bSMALLINCREMENTS\b""") { "SMALLSERIAL" },
            RegexReplacement("""\bINCREMENTS\b""") { "SERIAL" },
            RegexReplacement("""\bBINARY\b""") { "BYTEA" },
            RegexReplacement("""\bDOUBLE\b(?!\s+PRECISION)""") { "DOUBLE PRECISION" },
            RegexReplacement("""\bDATETIME(?:\((\d+)\))?\b""") { match ->
                match.groups[1]?.value?.let { precision -> "TIMESTAMP($precision)" } ?: "TIMESTAMP"
            }
        )

        private val UNSUPPORTED_SQL_PATTERNS = listOf(
            Regex("""\bTINYINT\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bMEDIUMINT\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bYEAR\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bVARBINARY\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTINYBLOB\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bMEDIUMBLOB\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bLONGBLOB\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bBLOB\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTINYTEXT\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bMEDIUMTEXT\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bLONGTEXT\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bENUM\s*\(""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bSET\s*\(""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bGEOMETRY\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bLINESTRING\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bGEOMETRYCOLLECTION\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bMULTIPOINT\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bMULTILINESTRING\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bMULTIPOLYGON\b""", setOf(RegexOption.IGNORE_CASE))
        )
    }
}
