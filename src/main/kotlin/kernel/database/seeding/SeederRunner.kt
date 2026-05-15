package kernel.database.seeding

import kernel.concurrency.BlockingTaskRunner
import kernel.database.DB
import kernel.foundation.Application
import kotlinx.coroutines.runBlocking
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class SeederRunner(
    private val app: Application,
    private val tasks: BlockingTaskRunner,
    private val defaultSeeder: KClass<out Seeder>,
    private val catalog: Set<KClass<out Seeder>> = emptySet()
) {
    private val activeExecution = ThreadLocal<MutableSet<String>?>()

    fun run(
        seederClass: KClass<out Seeder>? = null
    ): List<String> {
        val target = seederClass ?: defaultSeeder
        val executed = linkedSetOf<String>()

        tasks.run {
            runBlocking {
                val previous = activeExecution.get()
                activeExecution.set(executed)
                try {
                    execute(target, executed)
                } finally {
                    activeExecution.set(previous)
                }
            }
        }

        return executed.toList()
    }

    fun resolveSeeder(name: String): KClass<out Seeder> {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) {
            "Debes indicar un nombre de seeder valido."
        }

        catalog.firstOrNull { candidate ->
            candidate.qualifiedName == normalized || candidate.simpleName == normalized
        }?.let { return it }

        val loaded = runCatching {
            Class.forName(normalized).kotlin
        }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        return loaded
            ?.takeIf { Seeder::class.java.isAssignableFrom(it.java) }
            ?.let { it as KClass<out Seeder> }
            ?: error("No existe un seeder registrado con el nombre `$name`.")
    }

    internal suspend fun runSeeder(
        seederClass: KClass<out Seeder>
    ) {
        execute(seederClass, activeExecution.get() ?: linkedSetOf())
    }

    private suspend fun execute(
        seederClass: KClass<out Seeder>,
        executed: MutableSet<String>
    ) {
        val seeder = instantiate(seederClass)
        val name = seederClass.simpleName ?: seederClass.qualifiedName ?: "Seeder"

        if (seeder is DatabaseSeeder) {
            seeder.attachRunner(this)
            seeder.run()
            executed += name
            return
        }

        if (seeder.connectionName != null) {
            DB.transaction(connectionName = seeder.connectionName) {
                seeder.run()
            }
        } else {
            seeder.run()
        }
        executed += name
    }

    private fun instantiate(seederClass: KClass<out Seeder>): Seeder {
        val primaryConstructor = seederClass.primaryConstructor
        if (primaryConstructor != null) {
            primaryConstructor.isAccessible = true
            val parameters = primaryConstructor.parameters
            return when {
                parameters.isEmpty() -> primaryConstructor.call()
                parameters.size == 1 -> primaryConstructor.call(app)
                else -> error("El seeder `${seederClass.qualifiedName}` debe tener constructor vacío o `(Application)`.")
            }
        }

        seederClass.constructors.firstOrNull { constructor ->
            constructor.parameters.isEmpty()
        }?.let { constructor ->
            constructor.isAccessible = true
            return constructor.call()
        }

        seederClass.constructors.firstOrNull { constructor ->
            constructor.parameters.size == 1
        }?.let { constructor ->
            constructor.isAccessible = true
            return constructor.call(app)
        }

        error("No se pudo instanciar el seeder `${seederClass.qualifiedName}`.")
    }
}
