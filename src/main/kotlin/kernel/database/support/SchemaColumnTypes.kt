package kernel.database.support

/**
 * Catalogo canonico de tipos y constructores del DSL de schema.
 *
 * No representa un motor concreto: funciona como lenguaje intermedio del
 * kernel y luego cada gramatica decide si lo usa tal cual o lo traduce.
 */
internal object SchemaColumnTypes {
    const val UUID = "UUID"
    const val TEXT = "TEXT"
    const val SMALLINT = "SMALLINT"
    const val INTEGER = "INTEGER"
    const val BIGINT = "BIGINT"
    const val SMALLSERIAL = "SMALLSERIAL"
    const val SERIAL = "SERIAL"
    const val BIGSERIAL = "BIGSERIAL"
    const val REAL = "REAL"
    const val DOUBLE_PRECISION = "DOUBLE PRECISION"
    const val MONEY = "MONEY"
    const val BOOLEAN = "BOOLEAN"
    const val BYTEA = "BYTEA"
    const val DATE = "DATE"
    const val JSON = "JSON"
    const val JSONB = "JSONB"
    const val XML = "XML"
    const val CIDR = "CIDR"
    const val INET = "INET"
    const val MACADDR = "MACADDR"
    const val MACADDR8 = "MACADDR8"
    const val POINT = "POINT"
    const val LINE = "LINE"
    const val LSEG = "LSEG"
    const val BOX = "BOX"
    const val PATH = "PATH"
    const val POLYGON = "POLYGON"
    const val CIRCLE = "CIRCLE"
    const val TSVECTOR = "TSVECTOR"
    const val TSQUERY = "TSQUERY"
    const val PG_LSN = "PG_LSN"
    const val PG_SNAPSHOT = "PG_SNAPSHOT"
    const val TXID_SNAPSHOT = "TXID_SNAPSHOT"
    const val INT4RANGE = "INT4RANGE"
    const val INT8RANGE = "INT8RANGE"
    const val NUMRANGE = "NUMRANGE"
    const val TSRANGE = "TSRANGE"
    const val TSTZRANGE = "TSTZRANGE"
    const val DATERANGE = "DATERANGE"
    const val INT4MULTIRANGE = "INT4MULTIRANGE"
    const val INT8MULTIRANGE = "INT8MULTIRANGE"
    const val NUMMULTIRANGE = "NUMMULTIRANGE"
    const val TSMULTIRANGE = "TSMULTIRANGE"
    const val TSTZMULTIRANGE = "TSTZMULTIRANGE"
    const val DATEMULTIRANGE = "DATEMULTIRANGE"

    /**
     * Construye `CHAR(length)`.
     */
    fun char(length: Int): String {
        return "CHAR(${requirePositive(length, "Longitud")})"
    }

    /**
     * Construye `VARCHAR(length)`.
     */
    fun varchar(length: Int): String {
        return "VARCHAR(${requirePositive(length, "Longitud")})"
    }

    /**
     * Construye `NUMERIC(precision, scale)` validando sus limites.
     */
    fun numeric(precision: Int, scale: Int): String {
        val normalizedPrecision = requirePositive(precision, "Precision")

        require(scale >= 0) { "La escala debe ser mayor o igual a cero." }
        require(scale <= normalizedPrecision) {
            "La escala no puede ser mayor que la precision."
        }

        return "NUMERIC($normalizedPrecision, $scale)"
    }

    /**
     * Construye `BIT` o `BIT(length)`.
     */
    fun bit(length: Int? = null): String {
        return sizedType("BIT", length)
    }

    /**
     * Construye `BIT VARYING` o `BIT VARYING(length)`.
     */
    fun bitVarying(length: Int? = null): String {
        return sizedType("BIT VARYING", length)
    }

    /**
     * Construye `TIME` con precision opcional.
     */
    fun time(precision: Int? = null): String {
        return temporalType("TIME", precision)
    }

    /**
     * Construye `TIMETZ` con precision opcional.
     */
    fun timeTz(precision: Int? = null): String {
        return temporalType("TIMETZ", precision)
    }

    /**
     * Construye `TIMESTAMP` con precision opcional.
     */
    fun timestamp(precision: Int? = null): String {
        return temporalType("TIMESTAMP", precision)
    }

    /**
     * Construye `TIMESTAMPTZ` con precision opcional.
     */
    fun timestampTz(precision: Int? = null): String {
        return temporalType("TIMESTAMPTZ", precision)
    }

    /**
     * Construye `INTERVAL`, incluyendo campos y precision opcionales.
     */
    fun interval(fields: String? = null, precision: Int? = null): String {
        val normalizedFields = fields?.trim()?.takeIf { value -> value.isNotEmpty() }
        val precisionClause = precision?.let { value ->
            "(${requirePrecision(value)})"
        }.orEmpty()

        return listOfNotNull("INTERVAL", normalizedFields)
            .joinToString(" ") + precisionClause
    }

    /**
     * Construye un tipo arreglo usando la sintaxis `tipo[]`.
     */
    fun array(elementType: String): String {
        val normalizedElementType = elementType.trim()

        require(normalizedElementType.isNotEmpty()) {
            "El tipo de arreglo no puede estar vacio."
        }

        return "$normalizedElementType[]"
    }

    /**
     * Agrega longitud a tipos que la soportan.
     */
    private fun sizedType(type: String, length: Int?): String {
        return length?.let { value ->
            "$type(${requirePositive(value, "Longitud")})"
        } ?: type
    }

    /**
     * Agrega precision a tipos temporales.
     */
    private fun temporalType(type: String, precision: Int?): String {
        return precision?.let { value ->
            "$type(${requirePrecision(value)})"
        } ?: type
    }

    /**
     * Valida la precision temporal admitida por el DSL actual del kernel.
     */
    private fun requirePrecision(value: Int): Int {
        require(value in 0..6) {
            "La precision debe estar entre 0 y 6."
        }

        return value
    }

    /**
     * Valida enteros positivos usados por tipos parametrizados.
     */
    private fun requirePositive(value: Int, label: String): Int {
        require(value > 0) { "$label debe ser mayor que cero." }

        return value
    }
}
