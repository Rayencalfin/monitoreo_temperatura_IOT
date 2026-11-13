
package cl.inacap.monitoreoambientaliot

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

object RepositorioUsuariosFirebase {

    fun registrar(
        correo: String,
        contrasena: String,
        rol: Rol,
        resultado: (String?) -> Unit
    ) {
        val administrador = FirebaseAuth.getInstance().currentUser

        if (administrador == null) {
            resultado("Debes iniciar sesión como administrador")
            return
        }

        val contexto = FirebaseApp.getInstance().applicationContext

        val opciones = FirebaseApp.getInstance().options

        val secundaria = FirebaseApp.getApps(contexto)
            .firstOrNull { it.name == "registro_secundario" }
            ?: FirebaseApp.initializeApp(
                contexto,
                opciones,
                "registro_secundario"
            )

        if (secundaria == null) {
            resultado("No se pudo inicializar Firebase")
            return
        }

        val authSecundaria = FirebaseAuth.getInstance(secundaria)
        val db = FirebaseFirestore.getInstance()

        authSecundaria.createUserWithEmailAndPassword(
            correo.trim(),
            contrasena
        ).addOnSuccessListener { autenticacion ->

            val uid = autenticacion.user?.uid

            if (uid == null) {
                authSecundaria.signOut()
                resultado("No se pudo obtener el identificador")
                return@addOnSuccessListener
            }

            val datos = hashMapOf(
                "correo" to correo.trim(),
                "rol" to rol.name,
                "activo" to true,
                "creadoPor" to administrador.uid
            )

            db.collection("usuarios")
                .document(uid)
                .set(datos)
                .addOnSuccessListener {
                    authSecundaria.signOut()
                    resultado(null)
                }
                .addOnFailureListener { error ->
                    authSecundaria.signOut()
                    resultado(
                        "Cuenta creada, pero falló el perfil: ${error.message}"
                    )
                }

        }.addOnFailureListener { error ->
            resultado("Error al registrar: ${error.message}")
        }
    }
}
