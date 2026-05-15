# Capa: Mail

Esta capa permite el envío de correos electrónicos mediante transportes configurables (actualmente SMTP).

## Mailables

Los Mailables son clases que representan un correo electrónico específico. Encapsulan la lógica de quién envía, a quién se envía, el asunto y el cuerpo.

```kotlin
class WelcomeMail(val userName: String) : Mailable() {
    override fun subject() = "¡Bienvenido a la plataforma!"
    
    override fun to() = listOf(MailAddress("user@example.com"))

    override fun body() = """
        Hola $userName,
        Gracias por registrarte.
    """.trimIndent()
}
```

### Configuración del Mailable
- `subject()`: Asunto del correo.
- `from()`: Dirección remitente (opcional, usa la de defecto si no se indica).
- `to()`: Lista de destinatarios.
- `replyTo()`: Dirección de respuesta.
- `headers()`: Cabeceras personalizadas.
- `contentType()`: Por defecto es `text/plain`, pero puedes cambiarlo a `text/html`.

---

## Envío de Correos (`MailManager`)

El `MailManager` es el encargado de procesar y enviar los Mailables.

### Envío Asíncrono (Recomendado)
Envía el correo en un hilo separado para no bloquear el hilo actual.
```kotlin
mailManager().send(WelcomeMail("Julio"))
```

### Envío Síncrono
Bloquea hasta que el correo se envíe.
```kotlin
mailManager().sendNow(WelcomeMail("Julio"))
```

---

## Configuración (`.env`)

La configuración se basa en el driver seleccionado (por defecto `smtp`).

```ini
MAIL_DEFAULT=smtp
MAIL_FROM_ADDRESS=no-reply@kernel.com
MAIL_FROM_NAME="Kernel Platform"

# Configuración del driver SMTP
MAIL_DRIVERS_SMTP_HOST=localhost
MAIL_DRIVERS_SMTP_PORT=1025
MAIL_DRIVERS_SMTP_USERNAME=
MAIL_DRIVERS_SMTP_PASSWORD=
MAIL_DRIVERS_SMTP_ENCRYPTION=
```
