package kernel.database.schema

import kernel.database.support.SchemaColumnTypes

/**
 * Tipos y helpers enfocados en MariaDB.
 */
internal interface MariaDbColumnBlueprintSupport {
    fun addColumn(name: String, type: String): ColumnDefinition

    fun tinyInt(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TINYINT)
    }

    fun tinyInteger(name: String): ColumnDefinition {
        return tinyInt(name)
    }

    fun mediumInt(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MEDIUMINT)
    }

    fun mediumInteger(name: String): ColumnDefinition {
        return mediumInt(name)
    }

    fun year(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.YEAR)
    }

    fun varBinary(name: String, length: Int): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.varBinary(length))
    }

    fun tinyBlob(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TINYBLOB)
    }

    fun blob(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.BLOB)
    }

    fun mediumBlob(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MEDIUMBLOB)
    }

    fun longBlob(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.LONGBLOB)
    }

    fun tinyText(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.TINYTEXT)
    }

    fun mediumText(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MEDIUMTEXT)
    }

    fun longText(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.LONGTEXT)
    }

    fun enumValues(name: String, vararg values: String): ColumnDefinition {
        require(values.isNotEmpty()) {
            "ENUM requiere al menos un valor."
        }

        return addColumn(name, SchemaColumnTypes.enumValues(values.toList()))
    }

    fun setValues(name: String, vararg values: String): ColumnDefinition {
        require(values.isNotEmpty()) {
            "SET requiere al menos un valor."
        }

        return addColumn(name, SchemaColumnTypes.setValues(values.toList()))
    }

    fun geometry(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.GEOMETRY)
    }

    fun lineString(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.LINESTRING)
    }

    fun geometryCollection(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.GEOMETRYCOLLECTION)
    }

    fun multiPoint(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MULTIPOINT)
    }

    fun multiLineString(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MULTILINESTRING)
    }

    fun multiPolygon(name: String): ColumnDefinition {
        return addColumn(name, SchemaColumnTypes.MULTIPOLYGON)
    }
}
