package kernel.database.grammars

import kernel.database.migrations.Migration

/**
 * Gramatica de MariaDB para el DSL canonico del kernel.
 *
 * El DSL todavia conserva varias primitivas nacidas en PostgreSQL, asi que esta
 * gramatica traduce el subconjunto portable al dialecto MariaDB y falla con un
 * error claro cuando detecta caracteristicas exclusivas de otro motor.
 */
class MariaDbGrammar : SchemaGrammar {
    override fun generateUp(migration: Migration): String {
        return generateUpStatements(migration).joinToString("\n\n")
    }

    override fun generateDown(migration: Migration): String {
        return generateDownStatements(migration).joinToString("\n\n")
    }

    override fun generateUpStatements(migration: Migration): List<String> {
        return migration.upSql().map(::translateStatement)
    }

    override fun generateDownStatements(migration: Migration): List<String> {
        return migration.downSql().map(::translateStatement)
    }

    private fun translateStatement(sql: String): String {
        val normalized = sql.trim()
        require(normalized.isNotEmpty()) {
            "No se puede traducir una sentencia SQL vacia a MariaDB."
        }

        translateDropIndex(normalized)?.let { translated ->
            return translated
        }

        ensureNoUnsupportedStatement(normalized)

        val translated = TOKEN_REPLACEMENTS.fold(normalized) { current, replacement ->
            replacement.apply(current)
        }

        ensureNoUnsupportedFragments(translated)

        return translated
    }

    private fun translateDropIndex(sql: String): String? {
        val match = DROP_INDEX_REGEX.matchEntire(sql) ?: return null
        val concurrently = match.groups["concurrently"]?.value
        val ifExists = match.groups["ifExists"]?.value
        val name = match.groups["name"]?.value.orEmpty()
        val table = match.groups["table"]?.value?.trim()

        require(concurrently == null) {
            "MariaDB no soporta `DROP INDEX CONCURRENTLY`: `$sql`."
        }
        require(!table.isNullOrEmpty()) {
            "MariaDB requiere el nombre de la tabla para `DROP INDEX`: `$sql`."
        }

        val existenceClause = if (ifExists != null) " IF EXISTS" else ""
        return "DROP INDEX$existenceClause $name ON $table;"
    }

    private fun ensureNoUnsupportedStatement(sql: String) {
        val unsupportedPrefix = UNSUPPORTED_STATEMENT_PREFIXES.firstOrNull { prefix ->
            sql.startsWith(prefix, ignoreCase = true)
        }

        require(unsupportedPrefix == null) {
            "La sentencia `$unsupportedPrefix` no esta soportada por MariaDB en el kernel actual."
        }
    }

    private fun ensureNoUnsupportedFragments(sql: String) {
        val unsupportedFragment = UNSUPPORTED_SQL_PATTERNS.firstOrNull { pattern ->
            pattern.containsMatchIn(sql)
        }

        require(unsupportedFragment == null) {
            "La sentencia no es compatible con MariaDB: `$sql`."
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
        private val DROP_INDEX_REGEX = Regex(
            pattern =
                """DROP INDEX(?<concurrently>\s+CONCURRENTLY)?(?<ifExists>\s+IF EXISTS)?\s+(?<name>[^\s;]+)(?:\s*/\*\s*table:\s*(?<table>[^*]+?)\s*\*/)?;""",
            options = setOf(RegexOption.IGNORE_CASE)
        )

        private val TOKEN_REPLACEMENTS = listOf(
            RegexReplacement("""\bgen_random_uuid\(\)""") { "UUID()" },
            RegexReplacement("""::jsonb\b""") { "" },
            RegexReplacement("""::json\b""") { "" },
            RegexReplacement("""\bBIGINCREMENTS\b""") { "BIGINT AUTO_INCREMENT" },
            RegexReplacement("""\bSMALLINCREMENTS\b""") { "SMALLINT AUTO_INCREMENT" },
            RegexReplacement("""\bINCREMENTS\b""") { "INT AUTO_INCREMENT" },
            RegexReplacement("""\bDOUBLE\s+PRECISION\b""") { "DOUBLE" },
            RegexReplacement("""\bTIMESTAMPTZ(?:\((\d+)\))?\b""") { match ->
                match.groups[1]?.value?.let { precision -> "TIMESTAMP($precision)" } ?: "TIMESTAMP"
            },
            RegexReplacement("""\bTIMETZ(?:\((\d+)\))?\b""") { match ->
                match.groups[1]?.value?.let { precision -> "TIME($precision)" } ?: "TIME"
            },
            RegexReplacement("""\bJSONB\b""") { "JSON" },
            RegexReplacement("""\bBINARY\b""") { "LONGBLOB" },
            RegexReplacement("""\bBYTEA\b""") { "LONGBLOB" },
            RegexReplacement("""\bSMALLSERIAL\b""") { "SMALLINT AUTO_INCREMENT" },
            RegexReplacement("""\bBIGSERIAL\b""") { "BIGINT AUTO_INCREMENT" },
            RegexReplacement("""\bSERIAL\b""") { "INT AUTO_INCREMENT" },
            RegexReplacement("""\bUUID\b(?!\s*\()""") { "CHAR(36)" },
            RegexReplacement("""\bGENERATED ALWAYS AS IDENTITY\b""") { "AUTO_INCREMENT" },
            RegexReplacement("""\bGENERATED BY DEFAULT AS IDENTITY\b""") { "AUTO_INCREMENT" }
        )

        private val UNSUPPORTED_STATEMENT_PREFIXES = listOf(
            "CREATE EXTENSION",
            "DROP EXTENSION",
            "CREATE SCHEMA",
            "DROP SCHEMA",
            "ALTER SCHEMA",
            "CREATE MATERIALIZED VIEW",
            "DROP MATERIALIZED VIEW",
            "REFRESH MATERIALIZED VIEW",
            "CREATE TYPE",
            "ALTER TYPE",
            "DROP TYPE",
            "CREATE DOMAIN",
            "DROP DOMAIN",
            "COMMENT ON",
            "CREATE FUNCTION",
            "DROP FUNCTION"
        )

        private val UNSUPPORTED_SQL_PATTERNS = listOf(
            Regex("""\b[A-Z0-9_]+\[\]""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bINTERVAL\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTSVECTOR\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTSQUERY\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bPG_LSN\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bPG_SNAPSHOT\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTXID_SNAPSHOT\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bINT4RANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bINT8RANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bNUMRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTSRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTSTZRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bDATERANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bINT4MULTIRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bINT8MULTIRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bNUMMULTIRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTSMULTIRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bTSTZMULTIRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bDATEMULTIRANGE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bINET\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bCIDR\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bMACADDR8?\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bLINE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bLSEG\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bBOX\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bPATH\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bPOLYGON\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bCIRCLE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bXML\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bMONEY\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bALTER\s+COLUMN\s+\w+\s+TYPE\b""", setOf(RegexOption.IGNORE_CASE)),
            Regex("""\bUSING\b""", setOf(RegexOption.IGNORE_CASE))
        )
    }
}
