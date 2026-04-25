package kernel.providers

import kernel.foundation.Application

/**
 * Contrato base para providers del framework y de la aplicacion.
 *
 * `register` se usa para registrar configuracion, bindings y servicios.
 * `boot` se ejecuta cuando la aplicacion ya termino de registrar providers.
 */
abstract class ServiceProvider(
    protected val app: Application
) {
    open fun register() {
    }

    open fun boot() {
    }
}
