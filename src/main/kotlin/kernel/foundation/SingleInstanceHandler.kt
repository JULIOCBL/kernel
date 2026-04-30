package kernel.foundation

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

class SingleInstanceHandler(private val appName: String = "kernel-app") {
    private val tempDir = System.getProperty("java.io.tmpdir")
    private val lockFile = File(tempDir, ".$appName.lock")
    private val messageFile = File(tempDir, ".$appName.msg")
    private var fileChannel: FileChannel? = null
    private var lock: FileLock? = null

    fun isPrimaryInstance(onUrlReceived: (String) -> Unit): Boolean {
        return try {
            fileChannel = FileChannel.open(lockFile.toPath(),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            lock = fileChannel?.tryLock()

            if (lock != null) {
                // Soy la instancia principal, vigilo el archivo de mensajes
                startMessagePolling(onUrlReceived)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun startMessagePolling(onUrlReceived: (String) -> Unit) {
        thread(isDaemon = true) {
            while (true) {
                if (messageFile.exists()) {
                    val url = messageFile.readText().trim()
                    if (url.isNotEmpty()) onUrlReceived(url)
                    messageFile.delete() // Limpiar mensaje procesado
                }
                Thread.sleep(200) // Polling ligero cada 200ms
            }
        }
    }

    fun sendUrlToPrimary(url: String) {
        try {
            messageFile.writeText(url)
        } catch (e: Exception) { /* Ignorar errores de escritura */ }
    }
}