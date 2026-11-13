
package cl.inacap.monitoreoambientaliot

// Permite administrar notificaciones.
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent

// Permite abrir la aplicación al tocar la alerta.
import android.content.Context
import android.content.Intent

// Controla permisos y versiones de Android.
import android.content.pm.PackageManager
import android.os.Build

// Configura sonido y vibración.
import android.media.AudioAttributes
import android.media.RingtoneManager

// Comprueba permisos de notificaciones.
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// Identificador del canal de alarmas.
private const val CANAL_ALARMAS = "alarmas_temperatura_v1"

// Identificador de la notificación.
private const val ID_ALARMA = 1001

// Crea el canal de notificaciones.
fun crearCanalAlarmas(context: Context) {

    // Los canales existen desde Android 8.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

        // Obtiene el sonido predeterminado de alarma.
        val sonido = RingtoneManager.getDefaultUri(
            RingtoneManager.TYPE_ALARM
        )

        // Configura cómo se reproduce el sonido.
        val atributos = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(
                AudioAttributes.CONTENT_TYPE_SONIFICATION
            )
            .build()

        // Crea un canal de importancia alta.
        val canal = NotificationChannel(
            CANAL_ALARMAS,
            "Alarmas de temperatura",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {

            description =
                "Alertas por temperatura ambiental elevada"

            // Activa la vibración.
            enableVibration(true)

            // Asigna el sonido de alarma.
            setSound(sonido, atributos)
        }

        // Registra el canal en Android.
        val administrador = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager

        administrador.createNotificationChannel(canal)
    }
}

// Muestra una alarma de temperatura elevada.
fun mostrarAlarmaTemperatura(
    context: Context,
    temperatura: Int,
    umbral: Int
) {

    // Crea el canal si todavía no existe.
    crearCanalAlarmas(context)

    // Comprueba el permiso en Android 13+.
    if (
        Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    // Abre MainActivity al tocar la notificación.
    val intent = Intent(
        context,
        MainActivity::class.java
    )

    val pendiente = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
    )

    // Construye la notificación de alerta.
    val notificacion = NotificationCompat.Builder(
        context,
        CANAL_ALARMAS
    )
        .setSmallIcon(
            android.R.drawable.ic_dialog_alert
        )
        .setContentTitle(
            "¡ALERTA DE TEMPERATURA ALTA!"
        )
        .setContentText(
            "Temperatura: $temperatura °C. Límite: $umbral °C."
        )
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(
                    "La temperatura simulada alcanzó " +
                            "$temperatura °C y el límite " +
                            "configurado es $umbral °C. " +
                            "Revisa el monitoreo ambiental."
                )
        )
        .setPriority(
            NotificationCompat.PRIORITY_HIGH
        )
        .setCategory(
            NotificationCompat.CATEGORY_ALARM
        )
        .setContentIntent(pendiente)
        .setAutoCancel(true)
        .build()

    // Publica la notificación.
    NotificationManagerCompat.from(context).notify(
        ID_ALARMA,
        notificacion
    )
}
