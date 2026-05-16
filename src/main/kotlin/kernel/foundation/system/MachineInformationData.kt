package kernel.foundation.system

/**
 * Contiene la información única extraída de la máquina local.
 */
data class MachineInformationData(
    val deviceName: String,
    val operatingSystem: String,
    val machineId: String,
    val diskSerial: String,
    val cpuName: String,
    val macAddress: String,
    val fingerprint: String
)
