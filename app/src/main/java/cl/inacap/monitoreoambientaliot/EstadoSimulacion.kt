
package cl.inacap.monitoreoambientaliot

// Permite crear variables que actualizan la interfaz.
import androidx.compose.runtime.*

// Controla los sensores y dispositivos simulados.
class EstadoSimulacion(
    private val configuracion: RepositorioConfiguracion
) {

    // Temperatura ambiental simulada.
    var temperatura by mutableIntStateOf(25)
        private set

    // Humedad ambiental simulada.
    var humedad by mutableIntStateOf(60)
        private set

    // Límite configurado por el administrador.
    var umbralTemperatura by mutableIntStateOf(
        configuracion.obtenerUmbral()
    )
        private set

    // Activación manual del ventilador.
    var ventiladorManual by mutableStateOf(false)
        private set

    // Activación manual de la bomba.
    var bombaManual by mutableStateOf(false)
        private set

    // Activación automática del ventilador.
    var ventiladorAutomatico by mutableStateOf(false)
        private set

    // Activación automática de la bomba.
    var bombaAutomatica by mutableStateOf(false)
        private set

    // Tiempo con temperatura alta sin disminuir.
    var segundosSinBajar by mutableIntStateOf(0)
        private set

    // Guarda la última temperatura observada.
    private var temperaturaAnterior = temperatura

    // Controla el tiempo de enfriamiento.
    private var segundosEnfriamiento = 0

    // Estado final del ventilador.
    val ventilador: Boolean
        get() = ventiladorManual || ventiladorAutomatico

    // Estado final de la bomba.
    val bombaAgua: Boolean
        get() = bombaManual || bombaAutomatica

    // Indica si existe temperatura alta.
    val alertaTemperatura: Boolean
        get() = temperatura >= umbralTemperatura

    // Temperatura crítica para activar la bomba.
    val umbralBomba: Int
        get() = umbralTemperatura + 5

    // Modifica y guarda el umbral.
    fun cambiarUmbral(valor: Int) {

        umbralTemperatura = valor.coerceIn(20, 35)

        configuracion.guardarUmbral(
            umbralTemperatura
        )

        // Reinicia la evaluación temporal.
        segundosSinBajar = 0
        temperaturaAnterior = temperatura

        evaluarAutomatizacion()
    }

    // Cambia la temperatura mediante el Slider.
    fun cambiarTemperatura(valor: Int) {

        temperatura = valor.coerceIn(15, 40)

        evaluarAutomatizacion()
    }

    // Simula nuevas lecturas ambientales.
    fun simularSensores() {

        cambiarTemperatura(
            (temperatura + 1).coerceAtMost(40)
        )

        humedad = (humedad + 1).coerceAtMost(100)
    }

    // Activa o desactiva manualmente el ventilador.
    fun cambiarVentilador(activar: Boolean) {
        ventiladorManual = activar
    }

    // Activa o desactiva manualmente la bomba.
    fun cambiarBomba(activar: Boolean) {
        bombaManual = activar
    }

    // Evalúa las condiciones automáticas.
    private fun evaluarAutomatizacion() {

        // Activa el ventilador cuando hay temperatura alta.
        ventiladorAutomatico =
            temperatura >= umbralTemperatura

        // Activa la bomba por temperatura crítica
        // o por 15 segundos sin disminución.
        bombaAutomatica =
            temperatura >= umbralBomba ||
                    (
                            temperatura >= umbralTemperatura &&
                                    segundosSinBajar >= 15
                            )

        // Reinicia el contador al normalizarse.
        if (temperatura < umbralTemperatura) {
            segundosSinBajar = 0
        }
    }

    // Avanza un segundo de simulación.
    fun avanzarSimulacion() {

        // Comprueba si la temperatura disminuyó.
        if (temperatura < temperaturaAnterior) {

            segundosSinBajar = 0

        } else if (temperatura >= umbralTemperatura) {

            // Cuenta los segundos sin disminución.
            segundosSinBajar =
                (segundosSinBajar + 1).coerceAtMost(15)

        } else {

            segundosSinBajar = 0
        }

        // Guarda la temperatura observada.
        temperaturaAnterior = temperatura

        // Actualiza los dispositivos automáticos.
        evaluarAutomatizacion()

        // Cuenta los segundos para el enfriamiento.
        segundosEnfriamiento++

        // Enfría cada dos segundos.
        if (segundosEnfriamiento >= 2) {

            segundosEnfriamiento = 0

            // El ventilador disminuye la temperatura.
            if (ventilador && temperatura > 25) {

                temperatura--

                // Reinicia el tiempo sin disminución.
                temperaturaAnterior = temperatura
                segundosSinBajar = 0
            }

            // Revisa nuevamente la automatización.
            evaluarAutomatizacion()
        }
    }

    // Explica el estado del ventilador.
    fun motivoVentilador(): String {

        return when {

            ventiladorAutomatico ->
                "ACTIVADO AUTOMÁTICAMENTE POR TEMPERATURA ALTA"

            ventiladorManual ->
                "ACTIVADO MANUALMENTE"

            else ->
                "DESACTIVADO"
        }
    }

    // Explica el estado de la bomba.
    fun motivoBomba(): String {

        return when {

            bombaAutomatica &&
                    temperatura >= umbralBomba ->
                "ACTIVADO AUTOMÁTICAMENTE POR TEMPERATURA CRÍTICA"

            bombaAutomatica ->
                "ACTIVADO AUTOMÁTICAMENTE POR TEMPERATURA PERSISTENTE"

            bombaManual ->
                "ACTIVADO MANUALMENTE"

            else ->
                "DESACTIVADO"
        }
    }
}
