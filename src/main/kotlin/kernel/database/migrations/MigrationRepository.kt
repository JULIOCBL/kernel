package kernel.database.migrations

/**
 * Contrato para persistir el historial de migraciones ejecutadas.
 */
interface MigrationRepository {
    fun getRan(): List<String>

    fun getMigrationBatches(): Map<String, Int>

    fun getMigrations(steps: Int): List<MigrationRecord>

    fun getLast(): List<MigrationRecord>

    fun log(migration: String, batch: Int)

    fun delete(record: MigrationRecord)

    fun getNextBatchNumber(): Int

    fun getLastBatchNumber(): Int

    fun createRepository()

    fun repositoryExists(): Boolean

    fun deleteRepository()

    fun setSource(name: String?)
}
