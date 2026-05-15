package kernel.debug

import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.net.URI
import java.net.URL
import java.nio.file.Path
import java.time.temporal.TemporalAccessor
import java.util.Date
import java.util.IdentityHashMap
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private object AnsiColor {
    const val RESET: String = "\u001B[0m"
    const val GREEN: String = "\u001B[0;32m"
    const val YELLOW: String = "\u001B[1;33m"
}

/**
 * Configuracion global de `dump()` y `dd()`.
 *
 * Si quieres quitar un limite por completo, ponlo en `null`.
 */
object DebugConfig {
    var maxDepth: Int? = null
    var maxCollectionItems: Int? = null
    var maxObjectFields: Int? = null
    var maxStringLength: Int? = null
    var maxStackFrames: Int? = null
}

/**
 * Señal interna para cortar la ejecución después de `dd()`.
 */
class DumpAndDieSignal private constructor() : RuntimeException(null, null, false, false) {
    companion object {
        val instance: DumpAndDieSignal = DumpAndDieSignal()
    }
}

interface DebugRenderable {
    fun toDebugValue(): Any?
}

object Debug {
    fun dump(vararg values: Any?, printer: (String) -> Unit = ::printAndFlush) {
        printer(DebugOutput.render("dump", values.toList()))
    }

    fun dd(vararg values: Any?, printer: (String) -> Unit = ::printAndFlush): Nothing {
        printer(DebugOutput.render("dd", values.toList()))
        throw DumpAndDieSignal.instance
    }
}

internal fun printAndFlush(message: String) {
    System.out.println(message)
    System.out.flush()
    System.err.flush()
}

private object DebugOutput {
    // Usamos StackWalker (Java 9+) en lugar de Thread.currentThread().stackTrace
    // Es muchísimo más rápido porque no instancia todo el stack trace en memoria.
    private val stackWalker = StackWalker.getInstance()

    fun render(methodName: String, values: List<Any?>): String {
        val caller = findCaller()
        val formattedValues = DebugFormatter.format(methodName, values)

        return buildString {
            if (caller != null) {
                append(AnsiColor.GREEN)
                append(caller)
                append(AnsiColor.RESET)
                appendLine()
                appendLine()
            }

            append(AnsiColor.YELLOW)
            append(formattedValues)
            append(AnsiColor.RESET)
        }
    }

    private fun findCaller(): String? {
        return stackWalker.walk { stream ->
            stream.filter(::isUserFrame)
                .findFirst()
                .orElse(null)
        }?.let { caller ->
            val simpleClassName = caller.className.substringAfterLast('.')
            val methodName = caller.methodName

            "$simpleClassName.$methodName()  ${caller.fileName}:${caller.lineNumber}"
        }
    }

    private fun isUserFrame(frame: StackWalker.StackFrame): Boolean {
        val className = frame.className
        val fileName = frame.fileName ?: return false

        return className != Thread::class.java.name &&
                frame.lineNumber > 0 &&
                fileName.endsWith(".kt") &&
                !isDebugInfrastructure(className) &&
                !className.startsWith("java.") &&
                !className.startsWith("javax.") &&
                !className.startsWith("jdk.") &&
                !className.startsWith("sun.") &&
                !className.startsWith("kotlin.") &&
                !className.startsWith("org.junit.") &&
                !className.startsWith("org.gradle.") &&
                !className.startsWith("worker.org.gradle.")
    }

    private fun isDebugInfrastructure(className: String): Boolean {
        return className.startsWith("kernel.debug.") ||
                className == DumpAndDieSignal::class.java.name
    }
}

private object DebugFormatter {
    // CACHÉS PARA REFLEXIÓN Y CHEQUEOS (Ahorran miles de operaciones en objetos grandes)
    private val classFieldsCache = ConcurrentHashMap<Class<*>, List<Field>>()
    private val shouldRenderCache = ConcurrentHashMap<Class<*>, Boolean>()

    // CACHÉ DE IDENTACIÓN (Evita asignar miles de Strings en memoria para los espacios)
    private val indentCache = Array(32) { depth -> "  ".repeat(depth) }

    fun format(methodName: String, values: List<Any?>): String {
        if (values.isEmpty()) {
            return "$methodName()"
        }

        return if (values.size == 1) {
            "$methodName = ${render(values.first(), 0, IdentityHashMap())}"
        } else {
            values.mapIndexed { index, value ->
                "$methodName[$index] = ${render(value, 0, IdentityHashMap())}"
            }.joinToString("\n")
        }
    }

    private fun render(
        value: Any?,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        return when (value) {
            null -> "null"
            is DebugRenderable -> renderDebugRenderable(value, depth, seen)
            is String -> renderString(value)
            is Char -> "'${escapeChar(value)}'"
            is Number, is Boolean -> value.toString()
            is Enum<*> -> "${typeName(value.javaClass)}.${value.name}"
            is Throwable -> renderThrowable(value, depth, seen)
            is Path -> "\"${value.normalize()}\""
            is File -> "\"${value.toPath().normalize()}\""
            is URI, is URL, is UUID, is Date, is TemporalAccessor -> "\"$value\""
            is Class<*> -> "Class<${value.name}>"
            is Optional<*> -> renderOptional(value, depth, seen)
            is Pair<*, *> -> renderPair(value, depth, seen)
            is Triple<*, *, *> -> renderTriple(value, depth, seen)
            is Map<*, *> -> renderMap(value, depth, seen)
            is Sequence<*> -> renderSequence(value, depth, seen)
            is Iterator<*> -> renderIterator(value, depth, seen)
            is Iterable<*> -> renderIterable(value, depth, seen)
            is Array<*> -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            is IntArray -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            is LongArray -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            is ShortArray -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            is ByteArray -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            is DoubleArray -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            is FloatArray -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            is BooleanArray -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            is CharArray -> renderIndexedCollection(value, value.size, depth, seen) { index -> value[index] }
            else -> if (shouldRenderObject(value.javaClass)) {
                renderObject(value, depth, seen)
            } else {
                safeToString(value)
            }
        }
    }

    private fun renderDebugRenderable(
        value: DebugRenderable,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        val debugValue = runCatching { value.toDebugValue() }
            .getOrElse { error -> "<debug-render-error:${error::class.simpleName}>" }

        if (debugValue === value) {
            return "${typeName(value.javaClass)} {<self-debug-value>}"
        }

        return render(debugValue, depth, seen)
    }

    private fun renderString(value: String): String {
        val displayed = truncateString(value)

        val suffix = if (displayed.length < value.length) {
            "\" (truncated, length=${value.length})"
        } else {
            "\""
        }

        return "\"${escapeString(displayed)}$suffix"
    }

    private fun renderThrowable(
        value: Throwable,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        if (reachedMaxDepth(depth)) {
            return "${typeName(value.javaClass)} {<max-depth>}"
        }

        if (seen.put(value, Unit) != null) {
            return "${typeName(value.javaClass)} {<recursion>}"
        }

        val currentIndent = indent(depth)
        val nestedIndent = indent(depth + 1)
        val lines = mutableListOf<String>()

        value.message?.let { message ->
            lines += "${nestedIndent}message: ${renderString(message)}"
        }

        val stackFrames = value.stackTrace
        if (stackFrames.isNotEmpty()) {
            val visibleFrames = limitList(stackFrames.asList(), DebugConfig.maxStackFrames)
            val frameLines = visibleFrames.joinToString(",\n") { frame ->
                "$nestedIndent  ${renderString(frame.toString())}"
            }
            val moreFrames = stackFrames.size - visibleFrames.size
            val moreSuffix = if (moreFrames > 0) {
                ",\n$nestedIndent  ... +$moreFrames more"
            } else {
                ""
            }

            lines += "${nestedIndent}stackTrace: [\n$frameLines$moreSuffix\n${nestedIndent}]"
        }

        if (value.suppressed.isNotEmpty()) {
            lines += "${nestedIndent}suppressed: ${
                renderIndexedCollection(
                    identity = value.suppressed,
                    size = value.suppressed.size,
                    depth = depth + 1,
                    seen = seen
                ) { index -> value.suppressed[index] }
            }"
        }

        value.cause?.let { cause ->
            lines += "${nestedIndent}cause: ${render(cause, depth + 1, seen)}"
        }

        seen.remove(value)

        if (lines.isEmpty()) {
            return "${typeName(value.javaClass)} {}"
        }

        return "${typeName(value.javaClass)} {\n${lines.joinToString(",\n")}\n$currentIndent}"
    }

    private fun renderOptional(
        value: Optional<*>,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        if (value.isEmpty) {
            return "Optional.empty"
        }

        return "Optional(${render(value.orElse(null), depth + 1, seen)})"
    }

    private fun renderPair(
        value: Pair<*, *>,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        return renderNamedMembers(
            typeName = "Pair",
            depth = depth,
            members = listOf(
                "first" to value.first,
                "second" to value.second
            ),
            seen = seen,
            identity = value
        )
    }

    private fun renderTriple(
        value: Triple<*, *, *>,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        return renderNamedMembers(
            typeName = "Triple",
            depth = depth,
            members = listOf(
                "first" to value.first,
                "second" to value.second,
                "third" to value.third
            ),
            seen = seen,
            identity = value
        )
    }

    private fun renderObject(
        value: Any,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        val fields = collectFields(value.javaClass).map { field ->
            field.name to readFieldValue(field, value)
        }

        return renderNamedMembers(
            typeName = typeName(value.javaClass),
            depth = depth,
            members = fields,
            seen = seen,
            identity = value
        )
    }

    private fun renderMap(
        value: Map<*, *>,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        if (reachedMaxDepth(depth)) {
            return "{<max-depth>}"
        }

        if (value.isEmpty()) {
            return "{}"
        }

        if (seen.put(value, Unit) != null) {
            return "{<recursion>}"
        }

        val currentIndent = indent(depth)
        val nestedIndent = indent(depth + 1)
        val visibleEntries = limitList(value.entries.toList(), DebugConfig.maxCollectionItems)
        val entries = visibleEntries.map { (key, nestedValue) ->
            "$nestedIndent${render(key, depth + 1, seen)}: ${render(nestedValue, depth + 1, seen)}"
        }
        val remainingEntries = value.size - visibleEntries.size
        val lines = if (remainingEntries > 0) {
            entries + "$nestedIndent... +$remainingEntries more"
        } else {
            entries
        }

        seen.remove(value)

        return "{\n${lines.joinToString(",\n")}\n$currentIndent}"
    }

    private fun renderSequence(
        value: Sequence<*>,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        return renderUnknownSizeCollection(value, value.iterator(), depth, seen)
    }

    private fun renderIterator(
        value: Iterator<*>,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        return renderUnknownSizeCollection(value, value, depth, seen)
    }

    private fun renderIterable(
        value: Iterable<*>,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>
    ): String {
        val totalSize = (value as? Collection<*>)?.size
        return renderUnknownSizeCollection(value, value.iterator(), depth, seen, totalSize)
    }

    private fun shouldRenderObject(javaClass: Class<*>): Boolean {
        return shouldRenderCache.getOrPut(javaClass) {
            val className = javaClass.name
            !javaClass.isEnum &&
                    !javaClass.isAnonymousClass &&
                    !javaClass.isArray &&
                    !javaClass.isSynthetic &&
                    !className.startsWith("java.") &&
                    !className.startsWith("javax.") &&
                    !className.startsWith("jdk.") &&
                    !className.startsWith("sun.") &&
                    !className.startsWith("kotlin.")
        }
    }

    private fun collectFields(javaClass: Class<*>): List<Field> {
        return classFieldsCache.getOrPut(javaClass) {
            val hierarchy = generateSequence(javaClass) { current ->
                current.superclass?.takeUnless { it == Any::class.java }
            }.toList().asReversed()

            hierarchy.flatMap { current ->
                current.declaredFields
                    .asSequence()
                    .filterNot(::shouldIgnoreField)
                    .sortedBy(Field::getName)
                    .onEach { field -> field.trySetAccessible() }
                    .toList()
            }
        }
    }

    private fun shouldIgnoreField(field: Field): Boolean {
        return field.isSynthetic ||
                Modifier.isStatic(field.modifiers) ||
                field.name == "Companion" ||
                field.name == "INSTANCE" ||
                field.name.startsWith("\$")
    }

    private fun readFieldValue(field: Field, target: Any): Any? {
        return runCatching { field.get(target) }
            .getOrElse { error -> "<inaccessible:${error::class.simpleName}>" }
    }

    private fun renderUnknownSizeCollection(
        identity: Any,
        iterator: Iterator<*>,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>,
        totalSize: Int? = null
    ): String {
        if (reachedMaxDepth(depth)) {
            return "[<max-depth>]"
        }

        if (seen.put(identity, Unit) != null) {
            return "[<recursion>]"
        }

        val currentIndent = indent(depth)
        val nestedIndent = indent(depth + 1)
        val entries = mutableListOf<String>()
        var count = 0
        val limit = DebugConfig.maxCollectionItems

        while (iterator.hasNext() && withinItemLimit(count, limit)) {
            entries += "$nestedIndent${render(iterator.next(), depth + 1, seen)}"
            count++
        }

        val hasMore = iterator.hasNext()

        seen.remove(identity)

        if (entries.isEmpty() && !hasMore) {
            return "[]"
        }

        if (hasMore) {
            entries += "$nestedIndent${overflowLabel(totalSize, count)}"
        }

        return "[\n${entries.joinToString(",\n")}\n$currentIndent]"
    }

    private fun renderIndexedCollection(
        identity: Any,
        size: Int,
        depth: Int,
        seen: IdentityHashMap<Any, Unit>,
        valueAt: (Int) -> Any?
    ): String {
        if (reachedMaxDepth(depth)) {
            return "[<max-depth>]"
        }

        if (size == 0) {
            return "[]"
        }

        if (seen.put(identity, Unit) != null) {
            return "[<recursion>]"
        }

        val currentIndent = indent(depth)
        val nestedIndent = indent(depth + 1)
        val visibleSize = visibleCount(size, DebugConfig.maxCollectionItems)
        val entries = buildList {
            repeat(visibleSize) { index ->
                add("$nestedIndent${render(valueAt(index), depth + 1, seen)}")
            }
            if (size > visibleSize) {
                add("$nestedIndent... +${size - visibleSize} more")
            }
        }

        seen.remove(identity)

        return "[\n${entries.joinToString(",\n")}\n$currentIndent]"
    }

    private fun renderNamedMembers(
        typeName: String,
        depth: Int,
        members: List<Pair<String, Any?>>,
        seen: IdentityHashMap<Any, Unit>,
        identity: Any
    ): String {
        if (reachedMaxDepth(depth)) {
            return "$typeName {<max-depth>}"
        }

        if (seen.put(identity, Unit) != null) {
            return "$typeName {<recursion>}"
        }

        val currentIndent = indent(depth)
        val nestedIndent = indent(depth + 1)
        val visibleMembers = limitList(members, DebugConfig.maxObjectFields)
        val entries = visibleMembers.map { (name, memberValue) ->
            "$nestedIndent$name: ${render(memberValue, depth + 1, seen)}"
        }
        val remainingMembers = members.size - visibleMembers.size
        val lines = if (remainingMembers > 0) {
            entries + "$nestedIndent... +$remainingMembers fields"
        } else {
            entries
        }

        seen.remove(identity)

        if (lines.isEmpty()) {
            return "$typeName {}"
        }

        return "$typeName {\n${lines.joinToString(",\n")}\n$currentIndent}"
    }

    private fun overflowLabel(totalSize: Int?, visibleSize: Int): String {
        return if (totalSize != null && totalSize > visibleSize) {
            "... +${totalSize - visibleSize} more"
        } else {
            "... +more"
        }
    }

    private fun reachedMaxDepth(depth: Int): Boolean {
        val limit = DebugConfig.maxDepth ?: return false
        return depth >= limit
    }

    private fun withinItemLimit(count: Int, limit: Int?): Boolean {
        return limit == null || count < limit
    }

    private fun visibleCount(total: Int, limit: Int?): Int {
        return limit?.let { minOf(total, it) } ?: total
    }

    private fun <T> limitList(values: List<T>, limit: Int?): List<T> {
        return limit?.let(values::take) ?: values
    }

    private fun truncateString(value: String): String {
        val limit = DebugConfig.maxStringLength ?: return value
        return if (value.length > limit) {
            value.take(limit)
        } else {
            value
        }
    }

    private fun typeName(javaClass: Class<*>): String {
        return javaClass.simpleName.takeIf { it.isNotBlank() }
            ?: javaClass.name.substringAfterLast('.')
    }

    private fun safeToString(value: Any): String {
        return runCatching { value.toString() }
            .getOrElse { error -> "<toString-error:${error::class.simpleName}>" }
    }

    private fun indent(depth: Int): String {
        return if (depth < indentCache.size) indentCache[depth] else "  ".repeat(depth)
    }

    private fun escapeString(value: String): String {
        // Fast-path: Si no hay caracteres que escapar, devolvemos el String original
        // Esto ahorra memoria al no crear un StringBuilder por cada String puro que procesamos.
        if (value.none { it == '\\' || it == '"' || it == '\n' || it == '\r' || it == '\t' }) {
            return value
        }

        return buildString(value.length + 16) {
            value.forEach { character ->
                append(
                    when (character) {
                        '\\' -> "\\\\"
                        '"' -> "\\\""
                        '\n' -> "\\n"
                        '\r' -> "\\r"
                        '\t' -> "\\t"
                        else -> character
                    }
                )
            }
        }
    }

    private fun escapeChar(value: Char): String {
        return when (value) {
            '\\' -> "\\\\"
            '\'' -> "\\'"
            '\n' -> "\\n"
            '\r' -> "\\r"
            '\t' -> "\\t"
            else -> value.toString()
        }
    }
}
