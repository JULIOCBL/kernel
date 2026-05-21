package kernel.multisurface

/**
 * Catalogo de surfaces disponibles para la aplicacion.
 *
 * Permite listar surfaces, consultar sus caracteristicas y resolver una
 * configuracion concreta lista para abrir.
 */
class SurfaceCatalog(
    definitions: Iterable<SurfaceDefinition> = emptyList()
) {
    private val definitionsById = linkedMapOf<String, SurfaceDefinition>()

    init {
        definitions.forEach(::register)
    }

    fun register(definition: SurfaceDefinition): SurfaceCatalog {
        definitionsById[definition.id] = definition
        return this
    }

    fun all(): List<SurfaceDefinition> = definitionsById.values.toList()

    fun find(id: String): SurfaceDefinition? = definitionsById[id]

    fun require(id: String): SurfaceDefinition {
        return find(id) ?: error("No existe una definicion de surface para `$id`.")
    }

    fun contains(id: String): Boolean = definitionsById.containsKey(id)

    fun launchPlanFor(
        definitionId: String,
        options: SurfaceLaunchOptions = SurfaceLaunchOptions()
    ): SurfaceLaunchPlan {
        val definition = require(definitionId)
        return SurfaceLaunchPlan(
            definitionId = definition.id,
            descriptor = SurfaceDescriptor(
                id = definition.id,
                role = definition.role,
                title = options.title ?: definition.title,
                targetDisplayIndex = options.targetDisplayIndex ?: definition.targetDisplayIndex,
                fullscreen = options.fullscreen ?: definition.fullscreen,
                externalDisplayPreferred = options.externalDisplayPreferred
                    ?: definition.externalDisplayPreferred,
                visible = options.visible ?: definition.visible,
                capabilities = options.capabilities ?: definition.capabilities
            ),
            initialProjection = options.initialProjection ?: definition.defaultProjection
        )
    }
}
