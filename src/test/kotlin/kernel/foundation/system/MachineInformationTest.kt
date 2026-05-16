package kernel.foundation.system

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class MachineInformationTest {
    @Test
    fun testMachineInformationJson() {
        val json = MachineInformation.toJson()
        println(json)
        assertTrue(json.contains("\"device_name\":"))
        assertTrue(json.contains("\"operating_system\":"))
        assertTrue(json.contains("\"machine_id\":"))
        assertTrue(json.contains("\"fingerprint\":"))
        
        val data = MachineInformation.get()
        println("Device Name: ${data.deviceName}")
        println("OS: ${data.operatingSystem}")
        println("Machine ID: ${data.machineId}")
        println("Disk Serial: ${data.diskSerial}")
        println("CPU: ${data.cpuName}")
        println("MAC: ${data.macAddress}")
        println("Fingerprint: ${data.fingerprint}")
    }
}
