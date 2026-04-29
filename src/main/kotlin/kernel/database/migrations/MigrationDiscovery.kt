package kernel.database.migrations

import java.io.File
import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarFile
import kotlin.reflect.KClass

/**
 * Descubre migraciones automaticamente desde el classpath siguiendo una
 * convención de package y nombre de clase, similar al enfoque de Laravel con
 * su carpeta `database/migrations`.
 */
object MigrationDiscovery {
    fun discover(
        packageName: String,
        classLoader: ClassLoader = defaultClassLoader()
    ): MigrationRegistry {
        return MigrationRegistry(discoverDefinitions(packageName, classLoader))
    }

    fun discoverDefinitions(
        packageName: String,
        classLoader: ClassLoader = defaultClassLoader()
    ): List<MigrationDefinition> {
        val classNames = linkedSetOf<String>()
        val packagePath = packageName.replace('.', '/')
        val resources = classLoader.getResources(packagePath)

        while (resources.hasMoreElements()) {
            val resource = resources.nextElement()
            when (resource.protocol) {
                "file" -> scanDirectoryResource(resource, packageName, classNames)
                "jar" -> scanJarResource(resource, packagePath, classNames)
            }
        }

        return classNames.sorted().map { className ->
            definitionFromClassName(className, classLoader)
        }
    }

    private fun scanDirectoryResource(
        resource: URL,
        packageName: String,
        classNames: MutableSet<String>
    ) {
        val directory = Paths.get(resource.toURI())
        if (!Files.isDirectory(directory)) {
            return
        }

        Files.list(directory).use { files ->
            files.filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(::isCandidateClassFile)
                .forEach { fileName ->
                    val simpleName = fileName.removeSuffix(".class")
                    classNames += "$packageName.$simpleName"
                }
        }
    }

    private fun scanJarResource(
        resource: URL,
        packagePath: String,
        classNames: MutableSet<String>
    ) {
        val connection = resource.openConnection() as JarURLConnection
        val jarFile = connection.jarFile
        val expectedPrefix = "$packagePath/"

        jarFile.useEntries { entryName ->
            if (!entryName.startsWith(expectedPrefix)) {
                return@useEntries
            }

            val relativeName = entryName.removePrefix(expectedPrefix)
            if (relativeName.contains('/')) {
                return@useEntries
            }

            if (!isCandidateClassFile(relativeName)) {
                return@useEntries
            }

            classNames += entryName.removeSuffix(".class").replace('/', '.')
        }
    }

    private fun definitionFromClassName(
        className: String,
        classLoader: ClassLoader
    ): MigrationDefinition {
        val javaType = Class.forName(className, false, classLoader)

        require(Migration::class.java.isAssignableFrom(javaType)) {
            "La clase `$className` no extiende Migration."
        }

        @Suppress("UNCHECKED_CAST")
        val migrationType = javaType as Class<out Migration>
        val kotlinType = migrationType.kotlin as KClass<out Migration>
        val simpleName = migrationType.simpleName
            ?: throw IllegalArgumentException(
                "No se pudo resolver el nombre simple de la migracion `$className`."
            )

        return MigrationDefinition(
            name = simpleName,
            type = kotlinType
        ) {
            val constructor = migrationType.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance()
        }
    }

    private fun isCandidateClassFile(fileName: String): Boolean {
        if (!fileName.endsWith(".class")) {
            return false
        }

        val simpleName = fileName.removeSuffix(".class")
        return '$' !in simpleName && MIGRATION_CLASS_PATTERN.matches(simpleName)
    }

    private fun defaultClassLoader(): ClassLoader {
        return Thread.currentThread().contextClassLoader
            ?: MigrationDiscovery::class.java.classLoader
    }

    private inline fun JarFile.useEntries(block: (String) -> Unit) {
        val entries = entries()
        while (entries.hasMoreElements()) {
            block(entries.nextElement().name)
        }
    }

    private val MIGRATION_CLASS_PATTERN = Regex("""^M\d{4}_\d{2}_\d{2}_\d{6}_.+$""")
}
