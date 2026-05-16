package kernel.session

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class EncryptedStorage(
    private val path: Path
) {
    fun read(): ByteArray? {
        if (!path.exists()) {
            return null
        }

        val encrypted = path.inputStream().use { input -> input.readBytes() }
        if (encrypted.isEmpty()) {
            return ByteArray(0)
        }

        require(encrypted.size > IV_SIZE) {
            "El archivo de session en `${path.normalize()}` esta corrupto."
        }

        val iv = encrypted.copyOfRange(0, IV_SIZE)
        val ciphertext = encrypted.copyOfRange(IV_SIZE, encrypted.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), IvParameterSpec(iv))

        return cipher.doFinal(ciphertext)
    }

    fun write(data: ByteArray) {
        path.parent?.createDirectories()

        val iv = ByteArray(IV_SIZE).also(SECURE_RANDOM::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(data)
        val payload = iv + ciphertext

        val temporaryPath = path.resolveSibling("${path.fileName}.tmp")
        temporaryPath.outputStream().use { output ->
            output.write(payload)
            output.flush()
        }

        runCatching {
            Files.move(
                temporaryPath,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        }.recoverCatching {
            Files.move(
                temporaryPath,
                path,
                StandardCopyOption.REPLACE_EXISTING
            )
        }.getOrThrow()
    }

    companion object {
        private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
        private const val ALGORITHM = "AES"
        private const val IV_SIZE = 16
        private const val SECRET_KEY = "MY_SUPER_SECRET_KEY_32_BITS"
        private val SECURE_RANDOM = SecureRandom()

        private fun secretKey(): SecretKeySpec {
            val keyMaterial = MessageDigest.getInstance("SHA-256")
                .digest(SECRET_KEY.toByteArray(Charsets.UTF_8))

            return SecretKeySpec(keyMaterial, ALGORITHM)
        }
    }
}
