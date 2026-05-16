package kernel.session

interface SessionDriver {
    fun load(): Map<String, Any?>

    fun save(values: Map<String, Any?>)
}
