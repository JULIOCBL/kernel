package kernel.database.pdo.connections

import java.sql.Connection

/**
 * Contrato minimo para resolver conexiones nombradas de base de datos.
 *
 * Sigue la idea de Laravel: la capa superior no necesita saber como se
 * materializa la conexion, solo pedir una por nombre o usar la default.
 */
interface ConnectionResolver {
    fun connection(name: String? = null): Connection

    fun defaultConnectionName(): String

    fun connectionConfig(name: String? = null): DatabaseConnectionConfig
}
