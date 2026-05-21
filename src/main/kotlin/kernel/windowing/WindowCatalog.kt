package kernel.windowing

/**
 * Catalogo de pantallas hijas disponibles para la app.
 *
 * Permite listar pantallas, consultar sus caracteristicas y resolver una
 * configuracion concreta lista para abrir una instancia.
 */
class WindowCatalog(
    definitions: Iterable<WindowDefinition> = emptyList()
) {
    private val definitionsById = linkedMapOf<String, WindowDefinition>()

    init {
        definitions.forEach(::register)
    }

    fun register(definition: WindowDefinition): WindowCatalog {
        definitionsById[definition.id] = definition
        return this
    }

    fun all(): List<WindowDefinition> = definitionsById.values.toList()

    fun find(id: String): WindowDefinition? = definitionsById[id]

    fun require(id: String): WindowDefinition {
        return find(id) ?: error("No existe una definicion de ventana para `$id`.")
    }

    fun contains(id: String): Boolean = definitionsById.containsKey(id)

    fun descriptorFor(
        definitionId: String,
        instanceId: String,
        options: WindowLaunchOptions = WindowLaunchOptions()
    ): WindowDescriptor {
        val definition = require(definitionId)
        val mergedProps = when {
            options.props.isEmpty() -> definition.defaultProps
            definition.defaultProps.isEmpty() -> options.props
            else -> linkedMapOf<String, Any?>().apply {
                putAll(definition.defaultProps)
                putAll(options.props)
            }
        }

        return WindowDescriptor(
            instanceId = instanceId,
            definitionId = definition.id,
            title = options.title ?: definition.title,
            titleKey = options.titleKey ?: definition.titleKey,
            props = mergedProps,
            widthDp = options.widthDp ?: definition.widthDp,
            heightDp = options.heightDp ?: definition.heightDp,
            resizable = options.resizable ?: definition.resizable
        )
    }
}
