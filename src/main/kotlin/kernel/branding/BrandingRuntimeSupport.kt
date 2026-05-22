package kernel.branding

import kernel.foundation.Application
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object BrandingRuntimeSupport {
    fun installApplicationIcon(
        app: Application
    ) {
        val manager = app.config.get("services.branding.manager") as? BrandingManager
            ?: return
        val resourcePath = manager.windowIconPath()
            ?: manager.platformPackageIconPath()
            ?: return
        val image = loadImage(app, resourcePath) ?: return

        runCatching {
            if (Taskbar.isTaskbarSupported()) {
                val taskbar = Taskbar.getTaskbar()
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.iconImage = image
                    return
                }
            }
        }
    }

    private fun loadImage(
        app: Application,
        resourcePath: String
    ): BufferedImage? {
        val normalized = resourcePath.trim().removePrefix("/")
        val loader = app.javaClass.classLoader
        val stream = loader.getResourceAsStream(normalized) ?: return null

        return stream.use(ImageIO::read)
    }
}
