
package cl.inacap.monitoreoambientaliot

import android.content.Context

// Mantiene una única instancia de la simulación
// mientras el proceso de la aplicación esté activo.
object ControladorSimulacion {

    private var instancia: EstadoSimulacion? = null

    // Entrega la misma simulación a las pantallas
    // y, posteriormente, al servicio de monitoreo.
    @Synchronized
    fun obtener(context: Context): EstadoSimulacion {

        val actual = instancia

        if (actual != null) {
            return actual
        }

        val configuracion = RepositorioConfiguracion(
            context.applicationContext
        )

        val nueva = EstadoSimulacion(configuracion)

        instancia = nueva

        return nueva
    }
}
