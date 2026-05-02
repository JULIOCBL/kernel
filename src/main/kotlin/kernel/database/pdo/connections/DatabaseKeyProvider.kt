package kernel.database.pdo.connections

import javax.crypto.spec.SecretKeySpec

/**
 * Estrategia desacoplada y experimental para resolver llaves por conexion.
 *
 * El kernel ya no la usa en el flujo normal de conexiones JDBC; hoy permanece
 * como contrato opcional para pruebas o futuras integraciones donde una app
 * quiera reconstruir secretos sin acoplar el origen al caller.
 */
fun interface DatabaseKeyProvider {
    fun getEncryptionKey(connectionName: String): SecretKeySpec?
}
