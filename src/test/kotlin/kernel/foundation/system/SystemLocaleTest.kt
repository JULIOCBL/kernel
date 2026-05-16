package kernel.foundation.system

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class SystemLocaleTest {
    @Test
    fun testSystemLocaleExtraction() {
        val data = SystemLocale.get()
        assertNotNull(data.language)
        assertNotNull(data.timezone)
        assertNotNull(data.charset)
        
        val json = SystemLocale.toJson()
        println(json)
        assertTrue(json.contains("\"language\":"))
        assertTrue(json.contains("\"locale\":"))
        assertTrue(json.contains("\"timezone\":"))
        assertTrue(json.contains("\"charset\":"))
    }

    @Test
    fun testSystemLocaleObservation() {
        var notified = false
        val originalLocale = java.util.Locale.getDefault()
        
        SystemLocale.get() // Asegurar inicializacion
        
        SystemLocale.onChange { old, new ->
            notified = true
        }
        
        // Simular cambio
        java.util.Locale.setDefault(java.util.Locale.JAPAN)
        
        // Forzar check invocando el metodo privado para no hacer el test lento esperando 5s
        val method = SystemLocale::class.java.getDeclaredMethod("checkAndNotify")
        method.isAccessible = true
        method.invoke(SystemLocale)
        
        // Esperar al despacho del virtual thread
        Thread.sleep(200)
        
        assertTrue(notified, "El listener deberia haberse disparado al detectar el cambio de Locale")
        
        // Restaurar
        java.util.Locale.setDefault(originalLocale)
    }
}
