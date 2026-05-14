package kernel.database.seeding

import kernel.foundation.Application

abstract class Seeder(
    protected val app: Application
) {
    open val connectionName: String? = null

    abstract suspend fun run()
}
