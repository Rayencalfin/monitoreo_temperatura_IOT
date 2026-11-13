
package cl.inacap.monitoreoambientaliot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Servicio de supervisión ambiental simulada.
class MonitoreoService : Service() {

    companion object {
        private const val TAG = "MonitoreoService"
        private const val CANAL_MONITOREO = "canal_monitoreo_activo"
        private const val ID_MONITOREO = 2001
    }

    // Mantiene las tareas vinculadas al servicio.
    private val alcanceServicio =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Evita iniciar dos temporizadores.
    private var trabajoMonitoreo: Job? = null

    // Evita repetir la alarma durante la misma alerta.
    private var alarmaEmitida = false

    override fun onCreate() {
        super.onCreate()
        crearCanalMonitoreo()
        Log.d(TAG, "Servicio de monitoreo creado")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val notificacion = crearNotificacionMonitoreo()

        // Activa el servicio en primer plano.
        val tipoServicio =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ID_MONITOREO,
                notificacion,
                tipoServicio
            )
        } else {
            startForeground(
                ID_MONITOREO,
                notificacion
            )
        }

        // Inicia el temporizador una sola vez.
        if (trabajoMonitoreo?.isActive != true) {
            iniciarMonitoreo()
        }

        Log.d(TAG, "Monitoreo automático iniciado")

        return START_STICKY
    }

    // Ejecuta la simulación cada segundo.
    private fun iniciarMonitoreo() {

        val simulacion = ControladorSimulacion.obtener(
            applicationContext
        )

        // Comprueba si ya existía una alerta.
        alarmaEmitida = simulacion.alertaTemperatura

        trabajoMonitoreo = alcanceServicio.launch {

            while (isActive) {

                delay(1000)

                // Actualiza temperatura y dispositivos.
                simulacion.avanzarSimulacion()

                // Detecta si existe temperatura alta.
                val temperaturaAlta =
                    simulacion.alertaTemperatura

                if (temperaturaAlta && !alarmaEmitida) {

                    alarmaEmitida = true

                    mostrarAlarmaTemperatura(
                        context = applicationContext,
                        temperatura = simulacion.temperatura,
                        umbral = simulacion.umbralTemperatura
                    )

                    Log.d(
                        TAG,
                        "Alarma automática: ${simulacion.temperatura} °C"
                    )
                }

                // Permite una nueva alarma cuando
                // la temperatura vuelve a la normalidad.
                if (!temperaturaAlta) {
                    alarmaEmitida = false
                }
            }
        }
    }

    // Canal de notificación permanente.
    private fun crearCanalMonitoreo() {

        val canal = NotificationChannel(
            CANAL_MONITOREO,
            "Monitoreo ambiental activo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description =
                "Supervisión ambiental simulada en primer plano"
        }

        val administrador = getSystemService(
            NotificationManager::class.java
        )

        administrador.createNotificationChannel(canal)
    }

    // Notificación de supervisión activa.
    private fun crearNotificacionMonitoreo(): Notification {

        val intent = Intent(
            this,
            MainActivity::class.java
        )

        val pendiente = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(
            this,
            CANAL_MONITOREO
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Monitoreo Ambiental IoT")
            .setContentText("Supervisión ambiental activa")
            .setContentIntent(pendiente)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {

        // Detiene las tareas al finalizar el servicio.
        trabajoMonitoreo?.cancel()
        alcanceServicio.cancel()

        Log.d(TAG, "Servicio de monitoreo finalizado")

        super.onDestroy()
    }
}
