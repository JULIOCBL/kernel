package kernel.env

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Carga variables desde un archivo `.env`.
 *
 * El parser soporta líneas simples con formato `KEY=value`, ignora líneas en
 * blanco y comentarios que empiezan con `#`, y elimina espacios alrededor de la
 * clave y el valor.
 */
class EnvLoader(private val path: Path) {
    /**
     * Crea un loader usando una ruta en texto.
     */
    constructor(path: String) : this(Paths.get(path))

    /**
     * Crea un loader usando un `File`.
     */
    constructor(file: File) : this(file.toPath())

    /**
     * Lee el archivo `.env` y devuelve las variables encontradas.
     *
     * Si el archivo no existe, devuelve un mapa vacío para facilitar el uso en
     * entornos donde `.env` es opcional.
     */
    fun load(): Map<String, String> {
        if (!Files.exists(path)) {
            return emptyMap()
        }

        val values = linkedMapOf<String, String>()

        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            reader.lineSequence().forEachIndexed { index, line ->
                parseLine(line, index + 1)?.let { (key, value) ->
                    values[key] = value
                }
            }
        }

        return values.toMap()
    }

    /**
     * Parsea una línea individual del archivo.
     *
     * Las líneas inválidas lanzan una excepción con el número de línea para que
     * el error sea fácil de diagnosticar.
     */
    private fun parseLine(line: String, lineNumber: Int): Pair<String, String>? {
        val trimmed = line.trim()

        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null
        }

        val separatorIndex = trimmed.indexOf('=')

        if (separatorIndex <= 0) {
            throw IllegalArgumentException("Invalid .env entry at $path:$lineNumber. Expected KEY=value.")
        }

        val key = trimmed.substring(0, separatorIndex).trim()
        val value = trimmed.substring(separatorIndex + 1).trim()

        if (key.isEmpty()) {
            throw IllegalArgumentException("Invalid .env entry at $path:$lineNumber. Key must not be blank.")
        }

        return key to stripMatchingQuotes(value)
    }

    /**
     * Elimina comillas simples o dobles cuando envuelven todo el valor.
     */
    private fun stripMatchingQuotes(value: String): String {
        if (value.length < 2) {
            return value
        }

        val first = value.first()
        val last = value.last()

        return if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            value.substring(1, value.length - 1)
        } else {
            value
        }
    }
}
