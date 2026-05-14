package kernel.database.seeding

import kernel.foundation.Application
import kotlin.reflect.KClass

abstract class DatabaseSeeder(
    app: Application
) : Seeder(app) {
    private var runner: SeederRunner? = null

    internal fun attachRunner(runner: SeederRunner) {
        this.runner = runner
    }

    suspend fun call(vararg seeders: KClass<out Seeder>) {
        val activeRunner = runner
            ?: error("DatabaseSeeder no tiene un SeederRunner asociado.")

        seeders.forEach { seederClass ->
            activeRunner.runSeeder(seederClass)
        }
    }
}
