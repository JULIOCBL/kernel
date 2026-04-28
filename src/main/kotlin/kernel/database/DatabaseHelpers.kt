package kernel.database

import kernel.database.pdo.connections.DatabaseManager
import kernel.foundation.Application
import kernel.foundation.app

/**
 * Helper explicito para construir un `DatabaseManager` desde una `Application`.
 */
fun Application.databaseManager(): DatabaseManager = DatabaseManager.from(this)

/**
 * Helper global del runtime bootstrappeado para acceder al manager de bases de datos.
 */
fun databaseManager(): DatabaseManager = app().databaseManager()
