package kernel.foundation.system

import kernel.foundation.OS
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Provider estático para obtener información única de la máquina local.
 * Cachea los valores después de la primera ejecución para evitar latencias.
 */
object MachineInformation {
    const val DEVICE_NAME = "device_name"
    const val OPERATING_SYSTEM = "operating_system"
    const val MACHINE_ID = "machine_id"
    const val DISK_SERIAL = "disk_serial"
    const val CPU_NAME = "cpu_name"
    const val MAC_ADDRESS = "mac_address"
    const val FINGERPRINT = "fingerprint"

    private var cachedData: MachineInformationData? = null

    /**
     * Devuelve toda la información en formato de data class.
     */
    @Synchronized
    fun get(): MachineInformationData {
        if (cachedData == null) {
            cachedData = collectData()
        }
        return cachedData!!
    }

    /**
     * Obtiene un valor específico por medio de las constantes de la clase.
     */
    fun get(key: String): String {
        val data = get()
        return when (key) {
            DEVICE_NAME -> data.deviceName
            OPERATING_SYSTEM -> data.operatingSystem
            MACHINE_ID -> data.machineId
            DISK_SERIAL -> data.diskSerial
            CPU_NAME -> data.cpuName
            MAC_ADDRESS -> data.macAddress
            FINGERPRINT -> data.fingerprint
            else -> ""
        }
    }

    fun getDeviceName(): String = get(DEVICE_NAME)
    fun getOperatingSystem(): String = get(OPERATING_SYSTEM)
    fun getMachineId(): String = get(MACHINE_ID)
    fun getDiskSerial(): String = get(DISK_SERIAL)
    fun getCpuName(): String = get(CPU_NAME)
    fun getMacAddress(): String = get(MAC_ADDRESS)
    fun getFingerprint(): String = get(FINGERPRINT)

    /**
     * Exporta los datos a un Map plano.
     */
    fun toMap(): Map<String, String> {
        val data = get()
        return mapOf(
            DEVICE_NAME to data.deviceName,
            OPERATING_SYSTEM to data.operatingSystem,
            MACHINE_ID to data.machineId,
            DISK_SERIAL to data.diskSerial,
            CPU_NAME to data.cpuName,
            MAC_ADDRESS to data.macAddress,
            FINGERPRINT to data.fingerprint
        )
    }

    /**
     * Exporta los datos a formato JSON.
     */
    fun toJson(): String {
        val entries = toMap().entries.joinToString(",\n") { (key, value) ->
            "  \"$key\": \"${escapeJson(value)}\""
        }
        return "{\n$entries\n}"
    }

    private fun escapeJson(string: String): String {
        return string
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun collectData(): MachineInformationData {
        val deviceName = fetchDeviceName().clean()
        val operatingSystem = fetchOperatingSystem().clean()
        val machineId = fetchMachineId().clean()
        val diskSerial = fetchDiskSerial().clean()
        val cpuName = fetchCpuName().clean()
        val macAddress = fetchMacAddress().clean()

        val fingerprint = generateFingerprint(
            machineId = machineId,
            diskSerial = diskSerial,
            cpuName = cpuName,
            macAddress = macAddress,
            deviceName = deviceName
        )

        return MachineInformationData(
            deviceName = deviceName,
            operatingSystem = operatingSystem,
            machineId = machineId,
            diskSerial = diskSerial,
            cpuName = cpuName,
            macAddress = macAddress,
            fingerprint = fingerprint
        )
    }

    private fun fetchDeviceName(): String {
        return executeCommand("hostname")
    }

    private fun fetchOperatingSystem(): String {
        return System.getProperty("os.name") ?: ""
    }

    private fun fetchMachineId(): String {
        return when {
            OS.isWindows -> {
                val output = executeCommand("reg", "query", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid")
                output.substringAfter("REG_SZ").trim()
            }
            OS.isMac -> {
                val output = executeCommand("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                output.lines().firstOrNull { it.contains("IOPlatformUUID") }
                    ?.substringAfter("=")
                    ?.replace("\"", "")
                    ?.trim() ?: ""
            }
            OS.isLinux -> {
                var id = executeCommand("cat", "/etc/machine-id")
                if (id.isBlank()) {
                    id = executeCommand("cat", "/var/lib/dbus/machine-id")
                }
                id
            }
            else -> ""
        }
    }

    private fun fetchDiskSerial(): String {
        return when {
            OS.isWindows -> {
                val output = executeCommand("wmic", "diskdrive", "get", "serialnumber")
                output.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.equals("SerialNumber", ignoreCase = true) }
                    .firstOrNull() ?: ""
            }
            OS.isMac -> {
                val output = executeCommand("system_profiler", "SPHardwareDataType")
                output.lines()
                    .firstOrNull { it.contains("Serial Number", ignoreCase = true) }
                    ?.substringAfter(":")
                    ?.trim() ?: ""
            }
            OS.isLinux -> {
                val output = executeCommand("lsblk", "-no", "SERIAL")
                output.lines()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() } ?: ""
            }
            else -> ""
        }
    }

    private fun fetchCpuName(): String {
        return when {
            OS.isWindows -> {
                val output = executeCommand("wmic", "cpu", "get", "name")
                output.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.equals("Name", ignoreCase = true) }
                    .firstOrNull() ?: ""
            }
            OS.isMac -> {
                executeCommand("sysctl", "-n", "machdep.cpu.brand_string")
            }
            OS.isLinux -> {
                val output = executeCommand("cat", "/proc/cpuinfo")
                output.lines()
                    .firstOrNull { it.contains("model name", ignoreCase = true) }
                    ?.substringAfter(":")
                    ?.trim() ?: ""
            }
            else -> ""
        }
    }

    private fun fetchMacAddress(): String {
        return when {
            OS.isWindows -> {
                val output = executeCommand("getmac", "/NH", "/V")
                extractMacAddress(output)
            }
            OS.isMac -> {
                val output = executeCommand("ifconfig")
                extractMacAddress(output)
            }
            OS.isLinux -> {
                val output = executeCommand("ip", "link")
                extractMacAddress(output)
            }
            else -> ""
        }
    }

    private fun extractMacAddress(text: String): String {
        val regex = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})".toRegex()
        return regex.find(text)?.value?.replace("-", ":") ?: ""
    }

    private fun generateFingerprint(
        machineId: String,
        diskSerial: String,
        cpuName: String,
        macAddress: String,
        deviceName: String
    ): String {
        val payload = "$machineId|$diskSerial|$cpuName|$macAddress|$deviceName"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(payload.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    private fun executeCommand(vararg command: String): String {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().use { it.readText() }
            
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ""
            }

            if (process.exitValue() != 0) {
                return ""
            }

            output
        } catch (e: Exception) {
            ""
        }
    }

    private fun String.clean(): String {
        return this.trim().replace("\n", "").replace("\r", "").replace(Regex("\\s+"), " ")
    }
}
