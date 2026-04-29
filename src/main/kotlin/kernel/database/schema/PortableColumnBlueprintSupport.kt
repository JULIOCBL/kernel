package kernel.database.schema

import kernel.database.support.SchemaColumnTypes

/**
 * Tipos y atajos portables o ampliamente compartidos entre motores.
 */
internal interface PortableColumnBlueprintSupport {
    fun addColumn(name: String, type: String): ColumnDefinition

    fun id(name: String = "id"): ColumnDefinition {
        return uuid(name).notNull()
    }

    fun uuid(name: String = "id"): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.UUID)
    }

    fun foreignUuid(name: String): ColumnDefinition {
        return uuid(name)
    }

    fun char(name: String, length: Int): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.char(length))
    }

    fun character(name: String, length: Int): ColumnDefinition {
        return char(name, length)
    }

    fun varchar(name: String, length: Int): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.varchar(length))
    }

    fun characterVarying(name: String, length: Int): ColumnDefinition {
        return varchar(name, length)
    }

    fun string(name: String, length: Int = 255): ColumnDefinition {
        return varchar(name, length)
    }

    fun text(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TEXT)
    }

    fun int(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.INTEGER)
    }

    fun integer(name: String): ColumnDefinition {
        return int(name)
    }

    fun smallInt(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.SMALLINT)
    }

    fun smallInteger(name: String): ColumnDefinition {
        return smallInt(name)
    }

    fun bigInt(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.BIGINT)
    }

    fun foreignId(name: String): ColumnDefinition {
        return bigInt(name)
    }

    fun smallIncrements(name: String = "id"): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.SMALLINCREMENTS).notNull()
    }

    fun increments(name: String = "id"): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.INCREMENTS).notNull()
    }

    fun bigIncrements(name: String = "id"): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.BIGINCREMENTS).notNull()
    }

    fun real(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.REAL)
    }

    fun double(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.DOUBLE)
    }

    fun doublePrecision(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.DOUBLE_PRECISION)
    }

    fun boolean(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.BOOLEAN)
    }

    fun binary(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.BINARY)
    }

    fun bit(name: String, length: Int? = null): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.bit(length))
    }

    fun date(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.DATE)
    }

    fun time(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.time(precision))
    }

    fun timeTz(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.timeTz(precision))
    }

    fun dateTime(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.dateTime(precision))
    }

    fun timestamp(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.timestamp(precision))
    }

    fun timestampTz(name: String, precision: Int? = null): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.timestampTz(precision))
    }

    fun decimal(name: String, precision: Int, scale: Int): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.numeric(precision, scale))
    }

    fun numeric(name: String, precision: Int, scale: Int): ColumnDefinition {
        return decimal(name, precision, scale)
    }

    fun json(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.JSON)
    }

    fun point(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.POINT)
    }

    fun polygon(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.POLYGON)
    }

    fun timestamps() {
        timestamp("created_at")
        timestamp("updated_at")
    }

    fun timestampsTz() {
        timestampTz("created_at")
        timestampTz("updated_at")
    }

    fun softDeletes(name: String = "deleted_at"): ColumnDefinition {
        return timestamp(name)
    }

    fun softDeletesTz(name: String = "deleted_at"): ColumnDefinition {
        return timestampTz(name)
    }

    fun custom(name: String, type: String): ColumnDefinition {
        val normalizedType = type.trim()

        require(normalizedType.isNotEmpty()) {
            "El tipo de columna no puede estar vacio."
        }

        return addColumn(name, normalizedType)
    }
}
