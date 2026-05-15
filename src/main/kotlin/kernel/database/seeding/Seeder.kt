package kernel.database.seeding

import kernel.foundation.Application

abstract class Seeder(
    protected val app: Application
) {
    /**
     * Conexion opcional para envolver TODO el seeder en una transaccion.
     *
     * Si se deja en `null`, el seeder no fuerza una transaccion global y cada
     * modelo o `DB.table(...)` resuelve su propia conexion de forma natural.
     */
    open val connectionName: String? = null

    abstract suspend fun run()
}
