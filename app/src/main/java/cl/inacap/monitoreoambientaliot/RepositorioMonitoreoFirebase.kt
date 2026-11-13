
package cl.inacap.monitoreoambientaliot

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

object RepositorioMonitoreoFirebase {

    private val db = FirebaseFirestore.getInstance()

    fun guardarMedicion(simulacion: EstadoSimulacion) {

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            Log.d("FIREBASE_MONITOREO", "Sin sesión: medición no enviada")
            return
        }

        val datos = hashMapOf(
            "temperatura" to simulacion.temperatura,
            "humedad" to simulacion.humedad,
            "umbralTemperatura" to simulacion.umbralTemperatura,
            "ventiladorActivo" to simulacion.ventilador,
            "bombaActiva" to simulacion.bombaAgua,
            "alertaTemperatura" to simulacion.alertaTemperatura,
            "simulado" to true,
            "usuarioUid" to uid,
            "fecha" to FieldValue.serverTimestamp()
        )

        db.collection("monitoreos")
            .add(datos)
            .addOnSuccessListener {
                Log.d("FIREBASE_MONITOREO", "Medición guardada correctamente")
            }
            .addOnFailureListener { error ->
                Log.e("FIREBASE_MONITOREO", "Error al guardar medición", error)
            }
    }
}
