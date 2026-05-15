package kernel.command.commands

import kernel.command.Command
import kernel.command.CommandInput
import kernel.command.CommandResult
import kernel.security.SecureRuntimeDiagnostics

/**
 * Diagnostico seguro del runtime nativo KSR.
 *
 * No expone operaciones sensibles; solo reporta disponibilidad del binario y
 * resultado de carga JNI en el proceso actual.
 */
class SecureStatusCommand(
    private val statusResolver: () -> kernel.security.SecureRuntimeStatus = SecureRuntimeDiagnostics::currentStatus
) : Command {
    override val name: String = "secure:status"
    override val description: String =
        "Diagnostica el runtime nativo seguro del kernel. Ejemplo: ./kernel secure:status"
    override val usage: String = "secure:status"

    override fun execute(input: CommandInput): CommandResult {
        val status = statusResolver()

        return CommandResult(
            exitCode = 0,
            message = buildString {
                appendLine(CommandOutputStyle.info("Kernel Secure Runtime"))
                appendLine("OS: ${status.osName}")
                appendLine("Resource: ${status.resourcePath ?: "n/a"}")
                appendLine("Resource present: ${yesNo(status.resourcePresent)}")
                appendLine("Native loaded: ${yesNo(status.nativeLoaded)}")
                appendLine("Fragment B size: ${status.fragmentSize} bytes")
                if (status.loadError != null) {
                    append("Load error: ${status.loadError}")
                }
            }.trimEnd()
        )
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
}
