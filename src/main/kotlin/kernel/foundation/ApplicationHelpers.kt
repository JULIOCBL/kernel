package kernel.foundation

import java.nio.file.Path

/**
 * Devuelve la aplicacion activa del contexto global.
 */
fun app(): Application = ApplicationContext.current()

/**
 * Resuelve una ruta relativa desde la raiz de la aplicacion activa.
 */
fun basePath(relativePath: String = ""): Path = app().path(relativePath)
