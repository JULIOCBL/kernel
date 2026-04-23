package kernel.database.migrations.schema

import kernel.database.migrations.support.PostgreSqlColumnTypes

/**
 * DSL comun para declarar columnas PostgreSQL.
 */
abstract class PostgreSqlColumnBlueprint internal constructor() {
    /**
     * Atajo comun para una llave primaria UUID nativa de PostgreSQL.
     */
    fun id(name: String = "id"): ColumnDefinition {
        return uuid(name).notNull()
    }

    /**
     * Declara una columna `UUID`.
     */
    fun uuid(name: String = "id"): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.UUID)
    }

    /**
     * Declara una columna `UUID` pensada para foreign keys.
     */
    fun foreignUuid(name: String): ColumnDefinition {
        return uuid(name)
    }

    /**
     * Declara una columna `CHAR(length)`.
     */
    fun char(name: String, length: Int): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.char(length))
    }

    /**
     * Alias PostgreSQL para `char`.
     */
    fun character(name: String, length: Int): ColumnDefinition {
        return char(name, length)
    }

    /**
     * Declara una columna `VARCHAR(length)`.
     */
    fun varchar(name: String, length: Int): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.varchar(length))
    }

    /**
     * Alias PostgreSQL para `varchar`.
     */
    fun characterVarying(name: String, length: Int): ColumnDefinition {
        return varchar(name, length)
    }

    /**
     * Alias estilo Laravel para `VARCHAR`, con longitud 255 por defecto.
     */
    fun string(name: String, length: Int = 255): ColumnDefinition {
        return varchar(name, length)
    }

    /**
     * Declara una columna `TEXT`.
     */
    fun text(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.TEXT)
    }

    /**
     * Declara una columna `INTEGER`.
     */
    fun int(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.INTEGER)
    }

    /**
     * Alias expresivo para `int`.
     */
    fun integer(name: String): ColumnDefinition {
        return int(name)
    }

    /**
     * Declara una columna `SMALLINT`.
     */
    fun smallInt(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.SMALLINT)
    }

    /**
     * Alias expresivo para `smallInt`.
     */
    fun smallInteger(name: String): ColumnDefinition {
        return smallInt(name)
    }

    /**
     * Declara una columna `BIGINT`.
     */
    fun bigInt(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.BIGINT)
    }

    /**
     * Declara una columna `BIGINT` pensada para foreign keys.
     */
    fun foreignId(name: String): ColumnDefinition {
        return bigInt(name)
    }

    /**
     * Declara una columna `SMALLSERIAL`.
     */
    fun smallSerial(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.SMALLSERIAL)
    }

    /**
     * Declara una columna `SERIAL`.
     */
    fun serial(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.SERIAL)
    }

    /**
     * Declara una columna `BIGSERIAL`.
     */
    fun bigSerial(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.BIGSERIAL)
    }

    /**
     * Declara un `SMALLSERIAL NOT NULL` para llaves incrementales pequenas.
     */
    fun smallIncrements(name: String = "id"): ColumnDefinition {
        return smallSerial(name).notNull()
    }

    /**
     * Declara un `SERIAL NOT NULL` para llaves incrementales.
     */
    fun increments(name: String = "id"): ColumnDefinition {
        return serial(name).notNull()
    }

    /**
     * Declara un `BIGSERIAL NOT NULL` para llaves incrementales grandes.
     */
    fun bigIncrements(name: String = "id"): ColumnDefinition {
        return bigSerial(name).notNull()
    }

    /**
     * Declara una columna `REAL`.
     */
    fun real(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.REAL)
    }

    /**
     * Declara una columna `DOUBLE PRECISION`.
     */
    fun doublePrecision(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.DOUBLE_PRECISION)
    }

    /**
     * Declara una columna `MONEY`.
     */
    fun money(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.MONEY)
    }

    /**
     * Declara una columna `BOOLEAN`.
     */
    fun boolean(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.BOOLEAN)
    }

    /**
     * Declara una columna `BYTEA`.
     */
    fun bytea(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.BYTEA)
    }

    /**
     * Alias estilo Laravel para `bytea`.
     */
    fun binary(name: String): ColumnDefinition {
        return bytea(name)
    }

    /**
     * Declara una columna `BIT` con longitud opcional.
     */
    fun bit(name: String, length: Int? = null): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.bit(length))
    }

    /**
     * Declara una columna `BIT VARYING` con longitud opcional.
     */
    fun bitVarying(name: String, length: Int? = null): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.bitVarying(length))
    }

    /**
     * Alias PostgreSQL para `bitVarying`.
     */
    fun varbit(name: String, length: Int? = null): ColumnDefinition {
        return bitVarying(name, length)
    }

    /**
     * Declara una columna `DATE`.
     */
    fun date(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.DATE)
    }

    /**
     * Declara una columna `TIME` con precision opcional.
     */
    fun time(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.time(precision))
    }

    /**
     * Declara una columna `TIMETZ` con precision opcional.
     */
    fun timeTz(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.timeTz(precision))
    }

    /**
     * Declara una columna `TIMESTAMP` con precision opcional.
     */
    fun timestamp(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.timestamp(precision))
    }

    /**
     * Declara una columna `TIMESTAMPTZ` con precision opcional.
     */
    fun timestampTz(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.timestampTz(precision))
    }

    /**
     * Declara una columna `INTERVAL` con campos y precision opcionales.
     */
    fun interval(
        name: String,
        fields: String? = null,
        precision: Int? = null
    ): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.interval(fields, precision))
    }

    /**
     * Declara una columna `NUMERIC(precision, scale)`.
     */
    fun decimal(name: String, precision: Int, scale: Int): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.numeric(precision, scale))
    }

    /**
     * Alias PostgreSQL para `decimal`.
     */
    fun numeric(name: String, precision: Int, scale: Int): ColumnDefinition {
        return decimal(name, precision, scale)
    }

    /**
     * Declara una columna `JSONB`.
     */
    fun jsonb(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.JSONB)
    }

    /**
     * Declara una columna `JSON`.
     */
    fun json(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.JSON)
    }

    /**
     * Declara una columna `XML`.
     */
    fun xml(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.XML)
    }

    /**
     * Declara una columna `CIDR`.
     */
    fun cidr(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.CIDR)
    }

    /**
     * Declara una columna `INET`.
     */
    fun inet(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.INET)
    }

    /**
     * Declara una columna `MACADDR`.
     */
    fun macaddr(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.MACADDR)
    }

    /**
     * Declara una columna `MACADDR8`.
     */
    fun macaddr8(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.MACADDR8)
    }

    /**
     * Declara una columna geometrica `POINT`.
     */
    fun point(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.POINT)
    }

    /**
     * Declara una columna geometrica `LINE`.
     */
    fun line(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.LINE)
    }

    /**
     * Declara una columna geometrica `LSEG`.
     */
    fun lseg(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.LSEG)
    }

    /**
     * Declara una columna geometrica `BOX`.
     */
    fun box(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.BOX)
    }

    /**
     * Declara una columna geometrica `PATH`.
     */
    fun path(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.PATH)
    }

    /**
     * Declara una columna geometrica `POLYGON`.
     */
    fun polygon(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.POLYGON)
    }

    /**
     * Declara una columna geometrica `CIRCLE`.
     */
    fun circle(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.CIRCLE)
    }

    /**
     * Declara una columna `TSVECTOR` para busqueda de texto.
     */
    fun tsvector(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.TSVECTOR)
    }

    /**
     * Declara una columna `TSQUERY` para busqueda de texto.
     */
    fun tsquery(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.TSQUERY)
    }

    /**
     * Declara una columna `PG_LSN`.
     */
    fun pgLsn(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.PG_LSN)
    }

    /**
     * Declara una columna `PG_SNAPSHOT`.
     */
    fun pgSnapshot(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.PG_SNAPSHOT)
    }

    /**
     * Declara una columna `TXID_SNAPSHOT`.
     */
    fun txidSnapshot(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.TXID_SNAPSHOT)
    }

    /**
     * Declara una columna `INT4RANGE`.
     */
    fun int4Range(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.INT4RANGE)
    }

    /**
     * Declara una columna `INT8RANGE`.
     */
    fun int8Range(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.INT8RANGE)
    }

    /**
     * Declara una columna `NUMRANGE`.
     */
    fun numRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.NUMRANGE)
    }

    /**
     * Declara una columna `TSRANGE`.
     */
    fun tsRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.TSRANGE)
    }

    /**
     * Declara una columna `TSTZRANGE`.
     */
    fun tstzRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.TSTZRANGE)
    }

    /**
     * Declara una columna `DATERANGE`.
     */
    fun dateRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.DATERANGE)
    }

    /**
     * Declara una columna `INT4MULTIRANGE`.
     */
    fun int4MultiRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.INT4MULTIRANGE)
    }

    /**
     * Declara una columna `INT8MULTIRANGE`.
     */
    fun int8MultiRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.INT8MULTIRANGE)
    }

    /**
     * Declara una columna `NUMMULTIRANGE`.
     */
    fun numMultiRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.NUMMULTIRANGE)
    }

    /**
     * Declara una columna `TSMULTIRANGE`.
     */
    fun tsMultiRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.TSMULTIRANGE)
    }

    /**
     * Declara una columna `TSTZMULTIRANGE`.
     */
    fun tstzMultiRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.TSTZMULTIRANGE)
    }

    /**
     * Declara una columna `DATEMULTIRANGE`.
     */
    fun dateMultiRange(name: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.DATEMULTIRANGE)
    }

    /**
     * Declara una columna de arreglo usando la sintaxis `tipo[]`.
     */
    fun array(name: String, elementType: String): ColumnDefinition {
        return addColumn(name, PostgreSqlColumnTypes.array(elementType))
    }

    /**
     * Declara una columna basada en un tipo ENUM PostgreSQL creado previamente.
     */
    fun enumColumn(name: String, type: String): ColumnDefinition {
        return custom(name, type)
    }

    /**
     * Agrega `created_at` y `updated_at` como `TIMESTAMP`.
     */
    fun timestamps() {
        timestamp("created_at")
        timestamp("updated_at")
    }

    /**
     * Agrega `created_at` y `updated_at` como `TIMESTAMPTZ`.
     */
    fun timestampsTz() {
        timestampTz("created_at")
        timestampTz("updated_at")
    }

    /**
     * Declara una columna nullable para borrado logico con `TIMESTAMP`.
     */
    fun softDeletes(name: String = "deleted_at"): ColumnDefinition {
        return timestamp(name)
    }

    /**
     * Declara una columna nullable para borrado logico con `TIMESTAMPTZ`.
     */
    fun softDeletesTz(name: String = "deleted_at"): ColumnDefinition {
        return timestampTz(name)
    }

    /**
     * Declara una columna con un tipo PostgreSQL personalizado.
     */
    fun custom(name: String, type: String): ColumnDefinition {
        val normalizedType = type.trim()

        require(normalizedType.isNotEmpty()) {
            "El tipo de columna no puede estar vacio."
        }

        return addColumn(name, normalizedType)
    }

    /**
     * Punto de extension que materializa una columna en el builder concreto.
     */
    protected abstract fun addColumn(name: String, type: String): ColumnDefinition
}
