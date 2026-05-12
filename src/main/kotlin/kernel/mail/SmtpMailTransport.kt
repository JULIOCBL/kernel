package kernel.mail

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class SmtpMailTransport : MailTransport {
    override fun send(message: MailMessage, config: MailConfig): MailReceipt {
        require(config.host.isNotBlank()) { "mail.drivers.smtp.host no puede estar vacio." }
        require(config.port > 0) { "mail.drivers.smtp.port debe ser mayor a cero." }

        var socket: Socket = openSocket(config)
        var reader = socket.reader()
        var writer = socket.writer()

        expect(reader, 220)

        val ehloDomain = config.heloDomain?.takeIf(String::isNotBlank) ?: "localhost"
        sendCommand(writer, "EHLO $ehloDomain")
        expect(reader, 250)

        if (config.encryption.equals("tls", ignoreCase = true) ||
            config.encryption.equals("starttls", ignoreCase = true)
        ) {
            sendCommand(writer, "STARTTLS")
            expect(reader, 220)
            socket = wrapTls(socket, config.host, config.port)
            reader = socket.reader()
            writer = socket.writer()
            sendCommand(writer, "EHLO $ehloDomain")
            expect(reader, 250)
        }

        if (config.auth && !config.username.isNullOrBlank()) {
            authenticateLogin(reader, writer, config)
        }

        sendCommand(writer, "MAIL FROM:<${message.from.email}>")
        expect(reader, 250)

        message.to.forEach { recipient ->
            sendCommand(writer, "RCPT TO:<${recipient.email}>")
            expect(reader, 250, 251)
        }

        sendCommand(writer, "DATA")
        expect(reader, 354)
        writeMessage(writer, message)
        val deliveryResponse = expect(reader, 250)

        sendCommand(writer, "QUIT")
        expect(reader, 221)
        socket.close()

        return MailReceipt(
            messageId = deliveryResponse.extractMessageId(),
            acceptedRecipients = message.to.map { it.email },
            driver = "smtp",
            queued = false
        )
    }

    private fun openSocket(config: MailConfig): Socket {
        val useSsl = config.encryption.equals("ssl", ignoreCase = true) ||
            config.encryption.equals("smtps", ignoreCase = true)

        val socket = if (useSsl) {
            SSLSocketFactory.getDefault().createSocket() as SSLSocket
        } else {
            Socket()
        }

        socket.soTimeout = config.timeoutMillis
        socket.connect(InetSocketAddress(config.host, config.port), config.timeoutMillis)
        if (socket is SSLSocket) {
            socket.startHandshake()
        }

        return socket
    }

    private fun wrapTls(socket: Socket, host: String, port: Int): SSLSocket {
        val secured = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(socket, host, port, true) as SSLSocket
        secured.useClientMode = true
        secured.startHandshake()
        return secured
    }

    private fun authenticateLogin(
        reader: BufferedReader,
        writer: BufferedWriter,
        config: MailConfig
    ) {
        val username = config.username?.trim().orEmpty()
        val password = config.password?.trim().orEmpty()

        sendCommand(writer, "AUTH LOGIN")
        expect(reader, 334)
        sendCommand(writer, username.base64())
        expect(reader, 334)
        sendCommand(writer, password.base64())
        expect(reader, 235)
    }

    private fun writeMessage(writer: BufferedWriter, message: MailMessage) {
        val messageId = "<${java.util.UUID.randomUUID()}@kernel.local>"
        val lines = buildList {
            add("From: ${message.from.toHeaderValue()}")
            add("To: ${message.to.joinToString(", ") { it.toHeaderValue() }}")
            add("Subject: ${message.subject}")
            message.replyTo?.let { add("Reply-To: ${it.toHeaderValue()}") }
            add("MIME-Version: 1.0")
            add("Content-Type: ${message.contentType}")
            add("Message-ID: $messageId")
            message.headers.forEach { (key, value) -> add("$key: $value") }
            add("")
            addAll(
                message.body
                    .replace("\r\n", "\n")
                    .split('\n')
                    .map { line -> if (line.startsWith('.')) ".$line" else line }
            )
            add(".")
        }

        lines.forEach { line ->
            writer.write(line)
            writer.write("\r\n")
        }
        writer.flush()
    }

    private fun expect(reader: BufferedReader, vararg expectedCodes: Int): SmtpResponse {
        val response = readResponse(reader)
        require(response.code in expectedCodes) {
            "SMTP respondio ${response.code} cuando se esperaba ${expectedCodes.joinToString("/")}: ${response.message}"
        }
        return response
    }

    private fun readResponse(reader: BufferedReader): SmtpResponse {
        val lines = mutableListOf<String>()
        var code = 0

        while (true) {
            val line = reader.readLine() ?: error("El servidor SMTP cerro la conexion inesperadamente.")
            lines += line
            code = line.take(3).toIntOrNull()
                ?: error("Respuesta SMTP invalida: `$line`.")

            if (line.length < 4 || line[3] == ' ') {
                break
            }
        }

        return SmtpResponse(
            code = code,
            message = lines.joinToString("\n")
        )
    }

    private fun Socket.reader(): BufferedReader {
        return BufferedReader(InputStreamReader(getInputStream(), StandardCharsets.UTF_8))
    }

    private fun Socket.writer(): BufferedWriter {
        return BufferedWriter(OutputStreamWriter(getOutputStream(), StandardCharsets.UTF_8))
    }

    private fun sendCommand(writer: BufferedWriter, command: String) {
        writer.write(command)
        writer.write("\r\n")
        writer.flush()
    }

    private fun String.base64(): String {
        return Base64.getEncoder().encodeToString(toByteArray(StandardCharsets.UTF_8))
    }

    private data class SmtpResponse(
        val code: Int,
        val message: String
    ) {
        fun extractMessageId(): String {
            val candidate = Regex("<([^>]+)>").find(message)?.groupValues?.getOrNull(1)
            return candidate ?: java.util.UUID.randomUUID().toString()
        }
    }
}
