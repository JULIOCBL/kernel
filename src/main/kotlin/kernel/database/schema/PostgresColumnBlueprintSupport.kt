package kernel.database.schema

import kernel.database.support.SchemaColumnTypes

/**
 * Tipos y helpers exclusivos o fuertemente orientados a PostgreSQL.
 */
internal interface PostgresColumnBlueprintSupport {
    fun addColumn(name: String, type: String): ColumnDefinition

    fun smallSerial(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.SMALLSERIAL)
    }

    fun serial(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.SERIAL)
    }

    fun bigSerial(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.BIGSERIAL)
    }

    fun money(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MONEY)
    }

    fun bytea(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.BYTEA)
    }

    fun bitVarying(name: String, length: Int? = null): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.bitVarying(length))
    }

    fun varbit(name: String, length: Int? = null): ColumnDefinition {
        return bitVarying(name, length)
    }

    fun interval(
        name: String,
        fields: String? = null,
        precision: Int? = null
    ): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.interval(fields, precision))
    }

    fun jsonb(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.JSONB)
    }

    fun xml(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.XML)
    }

    fun cidr(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.CIDR)
    }

    fun inet(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.INET)
    }

    fun macaddr(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MACADDR)
    }

    fun macaddr8(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MACADDR8)
    }

    fun line(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.LINE)
    }

    fun lseg(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.LSEG)
    }

    fun box(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.BOX)
    }

    fun path(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.PATH)
    }

    fun circle(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.CIRCLE)
    }

    fun tsvector(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TSVECTOR)
    }

    fun tsquery(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TSQUERY)
    }

    fun pgLsn(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.PG_LSN)
    }

    fun pgSnapshot(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.PG_SNAPSHOT)
    }

    fun txidSnapshot(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TXID_SNAPSHOT)
    }

    fun int4Range(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.INT4RANGE)
    }

    fun int8Range(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.INT8RANGE)
    }

    fun numRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.NUMRANGE)
    }

    fun tsRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TSRANGE)
    }

    fun tstzRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TSTZRANGE)
    }

    fun dateRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.DATERANGE)
    }

    fun int4MultiRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.INT4MULTIRANGE)
    }

    fun int8MultiRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.INT8MULTIRANGE)
    }

    fun numMultiRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.NUMMULTIRANGE)
    }

    fun tsMultiRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TSMULTIRANGE)
    }

    fun tstzMultiRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TSTZMULTIRANGE)
    }

    fun dateMultiRange(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.DATEMULTIRANGE)
    }

    fun array(name: String, elementType: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.array(elementType))
    }

    fun enumColumn(name: String, type: String): ColumnDefinition {
        val normalizedType = type.trim()

        require(normalizedType.isNotEmpty()) {
            "El tipo enum no puede estar vacio."
        }

        return addColumn(name, normalizedType)
    }
}
