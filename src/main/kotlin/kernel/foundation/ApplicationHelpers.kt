package kernel.foundation

import java.nio.file.Path

/**
 * Devuelve la aplicacion global del proceso.
 */
fun app(): Application = ApplicationRuntime.current()

/**
 * Resuelve una ruta relativa desde la raiz de la aplicacion global.
 */
fun basePath(relativePath: String = ""): Path = app().path(relativePath)
