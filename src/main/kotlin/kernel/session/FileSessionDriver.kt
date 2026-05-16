package kernel.session

import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import java.nio.file.Path

class FileSessionDriver(
    private val path: Path,
    private val encrypt: Boolean = true,
    private val encryptedStorage: EncryptedStorage = EncryptedStorage(path)
) : SessionDriver {
    override fun load(): Map<String, Any?> {
        val bytes = if (encrypt) {
            encryptedStorage.read()
        } else {
            if (!path.exists()) {
                null
            } else {
                path.inputStream().use { input -> input.readBytes() }
            }
        } ?: return emptyMap()

        if (bytes.isEmpty()) {
            return emptyMap()
        }

        return SessionSerializer.deserialize(bytes)
    }

    override fun save(values: Map<String, Any?>) {
        val serialized = SessionSerializer.serialize(values)

        if (encrypt) {
            encryptedStorage.write(serialized)
            return
        }

        path.parent?.createDirectories()
        path.outputStream().use { output ->
            output.write(serialized)
            output.flush()
        }
    }
}
