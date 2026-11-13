
package cl.inacap.monitoreoambientaliot

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
// Contexto y ciclo de vida de Android.
import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.Log
// Permite solicitar permisos al usuario.
import android.Manifest
import com.google.firebase.firestore.FirebaseFirestore
import android.content.pm.PackageManager
import android.os.Build

// API moderna para solicitar permisos.
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// Comprueba permisos del teléfono.
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
// Componentes principales de Jetpack Compose.
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
//base de datos
import com.google.firebase.auth.FirebaseAuth

// Corrutinas para ejecutar la simulación periódica.
import kotlinx.coroutines.delay

// JSON para almacenar cuentas localmente.
import org.json.JSONObject

// Utilidades para proteger contraseñas de prueba.
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// ============================================
// 1. ROLES Y CUENTAS
// ============================================

// Define los roles permitidos.
enum class Rol {
    USUARIO,
    ADMINISTRADOR
}

// Representa una cuenta registrada.
data class Cuenta(
    val nombre: String,
    val rol: Rol,
    val bloqueado: Boolean = false,
    val uid: String = ""
)

// ============================================
// 2. ALMACENAMIENTO LOCAL DE USUARIOS
// ============================================

class RepositorioUsuarios(context: Context) {

    // Archivo privado para almacenar las cuentas.
    private val preferencias = context.getSharedPreferences(
        "usuarios_iot",
        Context.MODE_PRIVATE
    )

    init {
        // Crea un administrador de prueba si no existe.
        if (!preferencias.contains("cuenta_admin")) {
            guardarNuevoUsuario(
                "admin",
                "1234",
                Rol.ADMINISTRADOR
            )
        }
    }

    // Genera una clave única por nombre de usuario.
    private fun clave(nombre: String): String {
        return "cuenta_${nombre.trim().lowercase()}"
    }

    // Genera una sal aleatoria para la contraseña.
    private fun generarSal(): ByteArray {
        return ByteArray(16).also {
            SecureRandom().nextBytes(it)
        }
    }

    // Convierte la contraseña en un hash.
    private fun calcularHash(
        contrasena: String,
        sal: ByteArray
    ): ByteArray {

        // Configura PBKDF2 para el hash.
        val especificacion = PBEKeySpec(
            contrasena.toCharArray(),
            sal,
            120000,
            256
        )

        return try {
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(especificacion)
                .encoded
        } finally {
            // Limpia los datos temporales.
            especificacion.clearPassword()
        }
    }

    // Guarda una cuenta nueva en el dispositivo.
    private fun guardarNuevoUsuario(
        nombre: String,
        contrasena: String,
        rol: Rol
    ) {
        val sal = generarSal()
        val hash = calcularHash(contrasena, sal)

        // Organiza los datos como JSON.
        val datos = JSONObject().apply {
            put("nombre", nombre.trim())
            put("rol", rol.name)
            put("bloqueado", false)

            put(
                "sal",
                Base64.encodeToString(
                    sal,
                    Base64.NO_WRAP
                )
            )

            put(
                "hash",
                Base64.encodeToString(
                    hash,
                    Base64.NO_WRAP
                )
            )
        }

        // Guarda los datos permanentemente.
        preferencias.edit()
            .putString(
                clave(nombre),
                datos.toString()
            )
            .commit()
    }

    // Recupera los datos de una cuenta.
    private fun leerDatos(nombre: String): JSONObject? {

        val texto = preferencias.getString(
            clave(nombre),
            null
        ) ?: return null

        return try {
            JSONObject(texto)
        } catch (e: Exception) {
            null
        }
    }

    // Comprueba el formato del nombre.
    private fun nombreValido(nombre: String): Boolean {
        return nombre.matches(
            Regex("[a-zA-Z0-9_]{3,20}")
        )
    }

    // Registra una cuenta nueva.
    fun registrar(
        nombre: String,
        contrasena: String,
        rol: Rol = Rol.USUARIO
    ): String? {

        val limpio = nombre.trim()

        // Valida el nombre.
        if (!nombreValido(limpio)) {
            return "El usuario debe tener entre 3 y 20 caracteres, sin espacios"
        }

        // Exige al menos 8 caracteres.
        if (contrasena.length < 8) {
            return "La contraseña debe tener al menos 8 caracteres"
        }

        // Evita nombres repetidos.
        if (preferencias.contains(clave(limpio))) {
            return "El nombre de usuario ya existe"
        }

        guardarNuevoUsuario(
            limpio,
            contrasena,
            rol
        )

        // null significa operación exitosa.
        return null
    }

    // Valida el inicio de sesión.
    fun iniciarSesion(
        nombre: String,
        contrasena: String
    ): Cuenta? {

        val datos = leerDatos(nombre) ?: return null

        // Rechaza cuentas bloqueadas.
        if (datos.optBoolean("bloqueado", false)) {
            return null
        }

        return try {
            val sal = Base64.decode(
                datos.getString("sal"),
                Base64.NO_WRAP
            )

            val hashGuardado = Base64.decode(
                datos.getString("hash"),
                Base64.NO_WRAP
            )

            val hashIngresado = calcularHash(
                contrasena,
                sal
            )

            // Compara ambos hashes.
            if (!MessageDigest.isEqual(
                    hashGuardado,
                    hashIngresado
                )
            ) {
                null
            } else {
                Cuenta(
                    nombre = datos.getString("nombre"),
                    rol = Rol.valueOf(
                        datos.getString("rol")
                    ),
                    bloqueado = false
                )
            }

        } catch (e: Exception) {
            null
        }
    }

    // Obtiene todas las cuentas guardadas.
    fun listarUsuarios(): List<Cuenta> {

        return preferencias.all.keys
            .filter {
                it.startsWith("cuenta_")
            }
            .mapNotNull { llave ->

                try {
                    val texto = preferencias.getString(
                        llave,
                        null
                    ) ?: return@mapNotNull null

                    val datos = JSONObject(texto)

                    Cuenta(
                        nombre = datos.getString("nombre"),
                        rol = Rol.valueOf(
                            datos.getString("rol")
                        ),
                        bloqueado = datos.optBoolean(
                            "bloqueado",
                            false
                        )
                    )

                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy {
                it.nombre.lowercase()
            }
    }

    // Modifica el rol de una cuenta.
    fun cambiarRol(
        nombre: String,
        nuevoRol: Rol
    ): String? {

        if (nombre.equals("admin", true)) {
            return "No se puede cambiar el rol del administrador principal"
        }

        val datos = leerDatos(nombre)
            ?: return "Usuario no encontrado"

        datos.put("rol", nuevoRol.name)

        preferencias.edit()
            .putString(clave(nombre), datos.toString())
            .commit()

        return null
    }

    // Bloquea o desbloquea una cuenta.
    fun cambiarBloqueo(
        nombre: String,
        bloquear: Boolean
    ): String? {

        if (nombre.equals("admin", true)) {
            return "No se puede bloquear al administrador principal"
        }

        val datos = leerDatos(nombre)
            ?: return "Usuario no encontrado"

        datos.put("bloqueado", bloquear)

        preferencias.edit()
            .putString(clave(nombre), datos.toString())
            .commit()

        return null
    }

    // Cambia el nombre de una cuenta.
    fun modificarNombre(
        nombreActual: String,
        nuevoNombre: String
    ): String? {

        val limpio = nuevoNombre.trim()

        if (nombreActual.equals("admin", true)) {
            return "No se puede modificar el administrador principal"
        }

        if (!nombreValido(limpio)) {
            return "Nombre inválido: utiliza entre 3 y 20 caracteres"
        }

        val datos = leerDatos(nombreActual)
            ?: return "Usuario no encontrado"

        // Evita nombres duplicados.
        if (
            !nombreActual.equals(limpio, true) &&
            preferencias.contains(clave(limpio))
        ) {
            return "Ese nombre de usuario ya existe"
        }

        datos.put("nombre", limpio)

        // Elimina la clave antigua y guarda la nueva.
        preferencias.edit()
            .remove(clave(nombreActual))
            .putString(clave(limpio), datos.toString())
            .commit()

        return null
    }

    // Elimina una cuenta de prueba.
    fun eliminarUsuario(nombre: String): String? {

        if (nombre.equals("admin", true)) {
            return "No se puede eliminar al administrador principal"
        }

        if (!preferencias.contains(clave(nombre))) {
            return "Usuario no encontrado"
        }

        preferencias.edit()
            .remove(clave(nombre))
            .commit()

        return null
    }
}

// ============================================
// 3. CONFIGURACIÓN DE ALERTAS
// ============================================

class RepositorioConfiguracion(context: Context) {

    private val preferencias = context.getSharedPreferences(
        "configuracion_iot",
        Context.MODE_PRIVATE
    )

    private val db = com.google.firebase.firestore.FirebaseFirestore
        .getInstance()

    // Recupera el umbral guardado localmente.
    fun obtenerUmbral(): Int {
        return preferencias.getInt(
            "umbral_temperatura",
            30
        ).coerceIn(20, 35)
    }

    // Guarda el umbral localmente y en Firebase.
    fun guardarUmbral(valor: Int) {

        val umbral = valor.coerceIn(20, 35)

        // Mantiene el respaldo local.
        preferencias.edit()
            .putInt("umbral_temperatura", umbral)
            .apply()

        // Guarda la configuración en Firebase.
        val uid = com.google.firebase.auth.FirebaseAuth
            .getInstance()
            .currentUser?.uid

        if (uid != null) {

            val datos = hashMapOf(
                "umbralTemperatura" to umbral,
                "actualizadoPor" to uid,
                "fechaActualizacion" to
                        com.google.firebase.firestore.FieldValue
                            .serverTimestamp()
            )

            db.collection("configuracion")
                .document("general")
                .set(datos)
                .addOnSuccessListener {
                    android.util.Log.d(
                        "FIREBASE_CONFIG",
                        "Umbral guardado correctamente"
                    )
                }
                .addOnFailureListener { error ->
                    android.util.Log.e(
                        "FIREBASE_CONFIG",
                        "Error al guardar umbral",
                        error
                    )
                }
        }
    }
}

// ============================================
// 4. SIMULACIÓN DE SENSORES Y DISPOSITIVOS
// ============================================


// ============================================
// 5. ACTIVIDAD PRINCIPAL
// ============================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = FirebaseFirestore.getInstance()

        Log.d("FIREBASE_TEST", "Firebase inicializado correctamente")

        db.collection("prueba_conexion")
            .document("estado")
            .get()
            .addOnSuccessListener { documento ->
                Log.d(
                    "FIREBASE_TEST",
                    "Documento recibido: ${documento.getString("mensaje")}"
                )
            }
            .addOnFailureListener { error ->
                Log.e("FIREBASE_TEST", "Lectura bloqueada: ${error.message}")
            }
        // Prepara las cuentas guardadas.
        val usuarios = RepositorioUsuarios(
            applicationContext
        )

        // Prepara las alertas guardadas.
        val configuracion = RepositorioConfiguracion(
            applicationContext
        )

        // Construye la interfaz de la aplicación.
        setContent {
            MaterialTheme {
                AplicacionIoT(
                    usuarios,
                    configuracion
                )
            }
        }
    }
}

// ============================================
// 6. NAVEGACIÓN GENERAL
// ============================================

@Composable
fun AplicacionIoT(
    repositorio: RepositorioUsuarios,
    configuracion: RepositorioConfiguracion
) {

    // Cuenta que inició sesión.
    var cuentaActual by remember {
        mutableStateOf<Cuenta?>(null)
    }

    // Controla si aparece el registro.
    var mostrarRegistro by remember {
        mutableStateOf(false)
    }
// Obtiene el contexto de Android.
    val context = LocalContext.current
// Obtiene la simulación compartida.
    val simulacion = remember {
        ControladorSimulacion.obtener(context)
    }


// Prepara la solicitud del permiso.
    val permisoNotificaciones =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { concedido ->

            // Informa el resultado en Logcat.
            android.util.Log.d(
                "MonitoreoIoT",
                "Permiso de notificaciones: $concedido"
            )
        }

// Solicita permiso una vez al entrar a la app.
    LaunchedEffect(Unit) {

        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisoNotificaciones.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }
// Inicia la supervisión ambiental independientemente del login.
    LaunchedEffect(Unit) {
        val intent = Intent(
            context,
            MonitoreoService::class.java
        )

        ContextCompat.startForegroundService(
            context,
            intent
        )
    }

    val cuenta = cuentaActual

    when {

        // Si existe una sesión, revisa su rol.
        cuenta != null -> {

            if (cuenta.rol == Rol.ADMINISTRADOR) {

                PantallaAdministrador(
                    cuenta = cuenta,
                    repositorio = repositorio,
                    simulacion = simulacion,
                    cerrarSesion = {
                        cuentaActual = null
                        mostrarRegistro = false
                    }
                )

            } else {

                PantallaPrincipal(
                    cuenta = cuenta,
                    simulacion = simulacion,
                    cerrarSesion = {
                        cuentaActual = null
                        mostrarRegistro = false
                    }
                )
            }
        }

        // Muestra el formulario de registro.
        mostrarRegistro -> {

            PantallaRegistro(
                repositorio = repositorio,
                volverLogin = {
                    mostrarRegistro = false
                }
            )
        }

        // Sin sesión, muestra el login.
        else -> {

            PantallaLogin(
                repositorio = repositorio,
                iniciarSesion = {
                    cuentaActual = it
                },
                irRegistro = {
                    mostrarRegistro = true
                }
            )
        }
    }
}

// ============================================
// 7. LOGIN
// ============================================

@Composable
fun PantallaLogin(
    repositorio: RepositorioUsuarios,
    iniciarSesion: (Cuenta) -> Unit,
    irRegistro: () -> Unit
) {

    // Campos del formulario.
    var usuario by remember {
        mutableStateOf("")
    }

    var contrasena by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    var cargando by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val proteccion = remember {
        ProteccionIntentos(context)
    }

    var segundosBloqueo by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(usuario, segundosBloqueo > 0L) {
        if (segundosBloqueo > 0L) {
            while (true) {
                val restantes = proteccion.tiempoRestante(usuario)
                segundosBloqueo = restantes

                if (restantes <= 0L) break

                delay(1000)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            "Monitoreo Ambiental IoT",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(24.dp))

        // Campo para el usuario.
        OutlinedTextField(
            value = usuario,
            onValueChange = {
                usuario = it
                error = ""
            },
            label = { Text("Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Campo para la contraseña.
        OutlinedTextField(
            value = contrasena,
            onValueChange = {
                contrasena = it
                error = ""
            },
            label = { Text("Contraseña") },
            visualTransformation =
                PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Muestra errores de autenticación.
        if (error.isNotEmpty()) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(16.dp))

        // Valida las credenciales ingresadas.

        // Inicio de sesión con Firebase Authentication.
// Inicio de sesión con protección contra intentos fallidos.
        Button(
            onClick = {
                val correo = usuario.trim()
                val restantes = proteccion.tiempoRestante(correo)

                when {
                    restantes > 0L -> {
                        segundosBloqueo = restantes
                        error = "Acceso temporalmente bloqueado. Espera $restantes segundos."
                    }

                    correo.isBlank() || contrasena.isBlank() -> {
                        error = "Ingresa tu correo y contraseña"
                    }

                    else -> {
                        cargando = true
                        error = ""

                        auth.signInWithEmailAndPassword(correo, contrasena)
                            .addOnSuccessListener { resultado ->
                                val uid = resultado.user?.uid

                                if (uid == null) {
                                    cargando = false
                                    error = "No se pudo identificar al usuario"
                                    auth.signOut()
                                } else {
                                    db.collection("usuarios")
                                        .document(uid)
                                        .get()
                                        .addOnSuccessListener { documento ->
                                            cargando = false

                                            val activo = documento.getBoolean("activo") == true
                                            val rol = documento.getString("rol")

                                            if (!documento.exists() || !activo) {
                                                error = "Cuenta bloqueada o no autorizada"
                                                auth.signOut()
                                            } else if (
                                                rol != "ADMINISTRADOR" &&
                                                rol != "USUARIO"
                                            ) {
                                                error = "Rol no válido"
                                                auth.signOut()
                                            } else {
                                                proteccion.reiniciar(correo)
                                                segundosBloqueo = 0L

                                                iniciarSesion(
                                                    Cuenta(
                                                        nombre = correo,
                                                        rol = Rol.valueOf(rol),
                                                        bloqueado = false,
                                                        uid = uid
                                                    )
                                                )
                                            }
                                        }
                                        .addOnFailureListener {
                                            cargando = false
                                            error = "Error al consultar la base de datos"
                                            auth.signOut()
                                        }
                                }
                            }
                            .addOnFailureListener { excepcion ->
                                cargando = false

                                if (
                                    excepcion is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException ||
                                    excepcion is com.google.firebase.auth.FirebaseAuthInvalidUserException
                                ) {
                                    val bloqueo = proteccion.registrarFallo(correo)

                                    if (bloqueo > 0L) {
                                        segundosBloqueo = bloqueo
                                        error = "Demasiados intentos incorrectos. Bloqueo de $bloqueo segundos."
                                    } else {
                                        error = "Credenciales incorrectas. Intentos restantes: ${proteccion.intentosRestantes(correo)}"
                                    }
                                } else {
                                    error = "No se pudo iniciar sesión. Comprueba tu conexión e inténtalo nuevamente."
                                }
                            }
                    }
                }
            },
            enabled = !cargando && segundosBloqueo == 0L,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    cargando -> "Verificando..."
                    segundosBloqueo > 0L -> "Espera $segundosBloqueo segundos"
                    else -> "Iniciar sesión"
                }
            )
        }
    }


    // Abre el registro público.
        OutlinedButton(
            onClick = irRegistro,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Registrarse")
        }
    }


// ============================================
// 8. REGISTRO PÚBLICO
// ============================================

@Composable
fun PantallaRegistro(
    repositorio: RepositorioUsuarios,
    volverLogin: () -> Unit
) {

    var usuario by remember {
        mutableStateOf("")
    }

    var contrasena by remember {
        mutableStateOf("")
    }

    var confirmar by remember {
        mutableStateOf("")
    }

    var mensaje by remember {
        mutableStateOf("")
    }

    var registrado by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            "Registro de usuario",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(20.dp))

        // Nombre de la cuenta.
        OutlinedTextField(
            value = usuario,
            onValueChange = {
                usuario = it
                mensaje = ""
            },
            label = { Text("Nombre de usuario") },
            enabled = !registrado,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Contraseña de la cuenta.
        OutlinedTextField(
            value = contrasena,
            onValueChange = {
                contrasena = it
                mensaje = ""
            },
            label = { Text("Contraseña") },
            visualTransformation =
                PasswordVisualTransformation(),
            enabled = !registrado,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Confirma que ambas contraseñas coincidan.
        OutlinedTextField(
            value = confirmar,
            onValueChange = {
                confirmar = it
                mensaje = ""
            },
            label = { Text("Confirmar contraseña") },
            visualTransformation =
                PasswordVisualTransformation(),
            enabled = !registrado,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // Resultado del registro.
        if (mensaje.isNotEmpty()) {
            Text(
                mensaje,
                color = if (registrado) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }

        if (!registrado) {

            // Registra una cuenta con rol USUARIO.
            Button(
                onClick = {

                    if (contrasena != confirmar) {
                        mensaje = "Las contraseñas no coinciden"
                    } else {

                        val resultado = repositorio.registrar(
                            usuario,
                            contrasena
                        )

                        if (resultado == null) {
                            registrado = true
                            mensaje = "Cuenta creada correctamente"
                        } else {
                            mensaje = resultado
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Crear cuenta")
            }
        }

        // Regresa al login.
        OutlinedButton(
            onClick = volverLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver al inicio de sesión")
        }
    }
}

// ============================================
// 9. MONITOREO COMPARTIDO
// ============================================

@Composable
fun PanelMonitoreo(
    simulacion: EstadoSimulacion
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            "Panel de Monitoreo",
            style = MaterialTheme.typography.titleLarge
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                // Muestra la temperatura simulada.
                Text(
                    "Temperatura",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "${simulacion.temperatura} °C",
                    style = MaterialTheme.typography.headlineLarge
                )

                // Muestra la humedad simulada.
                Text(
                    "Humedad: ${simulacion.humedad} %"
                )

                // Muestra el límite configurado.
                Text(
                    "Umbral de alerta: ${simulacion.umbralTemperatura} °C"
                )

                // Comprueba si existe temperatura alta.
                if (simulacion.alertaTemperatura) {

                    Text(
                        "ALERTA: TEMPERATURA ALTA",
                        color = MaterialTheme.colorScheme.error
                    )

                } else {

                    Text("Temperatura normal")
                }

                // Alerta adicional para la bomba.
                if (simulacion.bombaAutomatica) {

                    Text(
                        "ALERTA: ENFRIAMIENTO ADICIONAL ACTIVADO",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Text("Simular temperatura")

        // Permite cambiar manualmente la temperatura.
        Slider(
            value = simulacion.temperatura.toFloat(),
            onValueChange = {
                simulacion.cambiarTemperatura(it.toInt())
            },
            valueRange = 15f..40f
        )

        // Simula una nueva lectura ambiental.

        Button(
            onClick = {
                simulacion.simularSensores()
                RepositorioMonitoreoFirebase.guardarMedicion(simulacion)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simular sensores")
        }


        // Informa los estados de los dispositivos.
        Text(
            "Ventilador: " +
                    if (simulacion.ventilador) {
                        "ACTIVADO"
                    } else {
                        "DESACTIVADO"
                    }
        )

        Text(
            "Bomba de agua: " +
                    if (simulacion.bombaAgua) {
                        "ACTIVADO"
                    } else {
                        "DESACTIVADO"
                    }
        )
    }
}

// ============================================
// 10. CONTROL COMPARTIDO DE DISPOSITIVOS
// ============================================

@Composable
fun PanelActivador(
    simulacion: EstadoSimulacion
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            "Panel de Activador",
            style = MaterialTheme.typography.titleLarge
        )

        // TARJETA DEL VENTILADOR
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    "Control del ventilador",
                    style = MaterialTheme.typography.titleMedium
                )

                // Estado actual del ventilador.
                Text(
                    if (simulacion.ventilador) {
                        "Estado: ACTIVADO"
                    } else {
                        "Estado: DESACTIVADO"
                    }
                )

                // Indica el motivo de activación.
                Text(simulacion.motivoVentilador())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text("Activación manual")

                    // Activa o desactiva la solicitud manual.
                    Switch(
                        checked = simulacion.ventiladorManual,
                        onCheckedChange = {
                            simulacion.cambiarVentilador(it)
                        }
                    )
                }

                Text(
                    "Temperatura actual: ${simulacion.temperatura} °C"
                )

                Text(
                    "Activación automática desde ${simulacion.umbralTemperatura} °C"
                )

                // Explica la prioridad automática.
                if (simulacion.ventiladorAutomatico) {

                    Text(
                        "El control automático mantiene ACTIVADO el ventilador.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // TARJETA DE LA BOMBA DE AGUA
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    "Control de la bomba de agua",
                    style = MaterialTheme.typography.titleMedium
                )

                // Estado actual de la bomba.
                Text(
                    if (simulacion.bombaAgua) {
                        "Estado: ACTIVADO"
                    } else {
                        "Estado: DESACTIVADO"
                    }
                )

                // Muestra por qué se activó.
                Text(simulacion.motivoBomba())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text("Activación manual")

                    // Cambia la solicitud manual de la bomba.
                    Switch(
                        checked = simulacion.bombaManual,
                        onCheckedChange = {
                            simulacion.cambiarBomba(it)
                        }
                    )
                }

                Text(
                    "Activación automática desde ${simulacion.umbralBomba} °C"
                )

                Text(
                    "También se activa tras 15 segundos de temperatura alta sin disminuir."
                )

                // Muestra el tiempo acumulado.
                if (simulacion.alertaTemperatura) {

                    Text(
                        "Tiempo sin disminuir: ${simulacion.segundosSinBajar} / 15 segundos"
                    )
                }

                if (simulacion.bombaAutomatica) {

                    Text(
                        "Control automático de la bomba ACTIVADO",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ============================================
// 11. PANTALLA DEL USUARIO
// ============================================

@Composable
fun PantallaPrincipal(
    cuenta: Cuenta,
    simulacion: EstadoSimulacion,
    cerrarSesion: () -> Unit
) {

    // Recuerda la sección seleccionada.
    var pantalla by remember {
        mutableStateOf("Monitoreo")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Text(
            "Monitoreo Ambiental IoT",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Bienvenido: ${cuenta.nombre}")
        Text("Rol: ${cuenta.rol.name}")

        // Menú de usuario.
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Button(
                onClick = {
                    pantalla = "Monitoreo"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Monitoreo")
            }

            Button(
                onClick = {
                    pantalla = "Activador"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Activador")
            }
        }

        HorizontalDivider()

        // Abre el componente seleccionado.
        when (pantalla) {

            "Monitoreo" -> {
                PanelMonitoreo(simulacion)
            }

            "Activador" -> {
                PanelActivador(simulacion)
            }
        }

        HorizontalDivider()

        // Finaliza la sesión.
        OutlinedButton(
            onClick = cerrarSesion,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
    }
}

// ============================================
// 12. PANEL DEL ADMINISTRADOR
// ============================================

@Composable
fun PantallaAdministrador(
    cuenta: Cuenta,
    repositorio: RepositorioUsuarios,
    simulacion: EstadoSimulacion,
    cerrarSesion: () -> Unit
) {

    // Sección inicial del administrador.
    var seccion by remember {
        mutableStateOf("Dashboard")
    }

    // Fuerza la actualización del listado.
    var revision by remember {
        mutableIntStateOf(0)
    }

    // Usuarios registrados en Firebase.
    var usuarios by remember {
        mutableStateOf<List<Cuenta>>(emptyList())
    }

// Mensaje en caso de error.
    var errorUsuarios by remember {
        mutableStateOf("")
    }

// Escucha los cambios de usuarios en Firebase.
    DisposableEffect(Unit) {

        val listener = FirebaseFirestore.getInstance()
            .collection("usuarios")
            .addSnapshotListener { documentos, error ->

                if (error != null) {
                    errorUsuarios = "Error al cargar usuarios: ${error.message}"
                    return@addSnapshotListener
                }

                errorUsuarios = ""

                usuarios = documentos?.documents?.mapNotNull { documento ->

                    val correo = documento.getString("correo")
                        ?: return@mapNotNull null

                    val rol = when (documento.getString("rol")) {
                        "ADMINISTRADOR" -> Rol.ADMINISTRADOR
                        "USUARIO" -> Rol.USUARIO
                        else -> return@mapNotNull null
                    }

                    Cuenta(
                        nombre = correo,
                        rol = rol,
                        bloqueado = documento.getBoolean("activo") != true,
                        uid = documento.id
                    )

                }?.sortedBy { it.nombre.lowercase() } ?: emptyList()
            }

        onDispose {
            listener.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        Text(
            "Panel de Administrador",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Bienvenido: ${cuenta.nombre}")
        Text("Rol: ADMINISTRADOR")

        HorizontalDivider()

        // MENÚ ADMINISTRATIVO
        Button(
            onClick = {
                seccion = "Monitoreo"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Monitoreo ambiental")
        }

        Button(
            onClick = {
                seccion = "Activador"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Control de dispositivos")
        }

        Button(
            onClick = {
                seccion = "Dashboard"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dashboard")
        }

        Button(
            onClick = {
                seccion = "Usuarios"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Usuarios")
        }

        Button(
            onClick = {
                seccion = "Dispositivos"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dispositivos")
        }

        Button(
            onClick = {
                seccion = "Alertas"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Alertas")
        }

        Button(
            onClick = {
                seccion = "Registros"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Registros")
        }

        HorizontalDivider()

        // CONTENIDO DE CADA SECCIÓN
        when (seccion) {

            "Monitoreo" -> {
                PanelMonitoreo(simulacion)
            }

            "Activador" -> {
                PanelActivador(simulacion)
            }

            "Dashboard" -> {

                Text(
                    "Resumen del sistema",
                    style = MaterialTheme.typography.titleLarge
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            "Usuarios registrados: ${usuarios.size}"
                        )

                        Text("Dispositivos simulados: 2")
                        Text("Sensores simulados: 2")

                        Text(
                            "Temperatura: ${simulacion.temperatura} °C"
                        )

                        Text(
                            "Humedad: ${simulacion.humedad} %"
                        )

                        Text(
                            "Umbral de alerta: ${simulacion.umbralTemperatura} °C"
                        )

                        Text("Sistema en modo simulación")
                    }
                }
            }

            "Usuarios" -> {

                // Muestra el CRUD administrativo.
                GestionUsuarios(
                    repositorio = repositorio,
                    usuarios = usuarios,
                    actualizar = {
                        revision++
                    }
                )
            }

            "Dispositivos" -> {

                Text(
                    "Dispositivos del sistema",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "Ventilador: " +
                            if (simulacion.ventilador) {
                                "ACTIVADO"
                            } else {
                                "DESACTIVADO"
                            }
                )

                Text(
                    "Bomba de agua: " +
                            if (simulacion.bombaAgua) {
                                "ACTIVADO"
                            } else {
                                "DESACTIVADO"
                            }
                )

                Text("Sensor de temperatura: SIMULADO")
                Text("Sensor de humedad: SIMULADO")
            }

            "Alertas" -> {

                // Permite configurar las alertas.
                PanelConfiguracionAlertas(simulacion)
            }

            "Registros" -> {

                Text(
                    "Registros del sistema",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    "El historial de eventos se implementará posteriormente."
                )
            }
        }

        HorizontalDivider()

        OutlinedButton(
            onClick = cerrarSesion,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }
    }
}

// ============================================
// 13. CONFIGURACIÓN ADMINISTRATIVA DE ALERTAS
// ============================================


@Composable
fun PanelConfiguracionAlertas(
    simulacion: EstadoSimulacion
) {

    // Obtiene el contexto de Android.
    val context = LocalContext.current

    // Organiza verticalmente los elementos.
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Botón para probar la alarma sonora.
        Button(
            onClick = {
                mostrarAlarmaTemperatura(
                    context = context,
                    temperatura = simulacion.umbralTemperatura + 5,
                    umbral = simulacion.umbralTemperatura
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Probar alarma sonora")
        }

        // Título de la sección.
        Text(
            "Configuración de alertas",
            style = MaterialTheme.typography.titleLarge
        )


        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    "Umbral de temperatura",
                    style = MaterialTheme.typography.titleMedium
                )

                // Muestra el umbral actual.
                Text(
                    "${simulacion.umbralTemperatura} °C",
                    style = MaterialTheme.typography.headlineLarge
                )

                // El administrador modifica el límite.
                Slider(
                    value = simulacion.umbralTemperatura.toFloat(),
                    onValueChange = {
                        simulacion.cambiarUmbral(it.toInt())
                    },
                    valueRange = 20f..35f,
                    steps = 14
                )

                Text(
                    "Rango permitido: 20 °C a 35 °C"
                )

                Text(
                    "Ventilador automático desde ${simulacion.umbralTemperatura} °C"
                )

                Text(
                    "Bomba automática desde ${simulacion.umbralBomba} °C"
                )

                Text(
                    "La bomba también se activa tras 15 segundos sin disminución de temperatura."
                )

                Text(
                    "La configuración se guarda automáticamente."
                )
            }
        }

        // Estado actual de las alertas.
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    "Estado de alertas",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Temperatura actual: ${simulacion.temperatura} °C"
                )

                if (simulacion.alertaTemperatura) {

                    Text(
                        "ALERTA: TEMPERATURA ALTA",
                        color = MaterialTheme.colorScheme.error
                    )

                } else {

                    Text("Temperatura normal")
                }

                if (simulacion.bombaAutomatica) {

                    Text(
                        "Bomba de agua activada automáticamente",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ============================================
// 14. CRUD DE USUARIOS
// ============================================

@Composable
fun GestionUsuarios(
    repositorio: RepositorioUsuarios,
    usuarios: List<Cuenta>,
    actualizar: () -> Unit
) {

    // Campos para crear una cuenta.
    var nuevoUsuario by remember {
        mutableStateOf("")
    }

    var nuevaContrasena by remember {
        mutableStateOf("")
    }

    var nuevoRol by remember {
        mutableStateOf(Rol.USUARIO)
    }

    var mensaje by remember {
        mutableStateOf("")
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            "Gestión de usuarios",
            style = MaterialTheme.typography.titleLarge
        )

        Text("Total de cuentas: ${usuarios.size}")

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    "Crear usuario",
                    style = MaterialTheme.typography.titleMedium
                )

// Correo electrónico del nuevo usuario.
                OutlinedTextField(
                    value = nuevoUsuario,
                    onValueChange = {
                        nuevoUsuario = it
                    },
                    label = {
                        Text("Correo electrónico")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier.fillMaxWidth()
                )


                // Contraseña de la nueva cuenta.
                OutlinedTextField(
                    value = nuevaContrasena,
                    onValueChange = {
                        nuevaContrasena = it
                    },
                    label = {
                        Text("Contraseña")
                    },
                    visualTransformation =
                        PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Rol de la nueva cuenta")

                // Selección del rol.
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    FilterChip(
                        selected = nuevoRol == Rol.USUARIO,
                        onClick = {
                            nuevoRol = Rol.USUARIO
                        },
                        label = {
                            Text("Usuario")
                        }
                    )

                    FilterChip(
                        selected =
                            nuevoRol == Rol.ADMINISTRADOR,
                        onClick = {
                            nuevoRol = Rol.ADMINISTRADOR
                        },
                        label = {
                            Text("Administrador")
                        }
                    )
                }

                // Guarda la cuenta y actualiza la lista.

                    Button(
                        onClick = {
                            val correo = nuevoUsuario.trim()
                            val errorContrasena =
                                SeguridadContrasenas.validar(nuevaContrasena)

                            when {
                                correo.isBlank() ||
                                        !android.util.Patterns.EMAIL_ADDRESS
                                            .matcher(correo).matches() -> {
                                    mensaje = "Ingresa un correo electrónico válido"
                                }

                                errorContrasena != null -> {
                                    mensaje = errorContrasena
                                }

                                else -> {
                                    mensaje = "Registrando usuario..."

                                    RepositorioUsuariosFirebase.registrar(
                                        correo = correo,
                                        contrasena = nuevaContrasena,
                                        rol = nuevoRol
                                    ) { resultado ->

                                        if (resultado == null) {
                                            mensaje = "Usuario registrado correctamente en Firebase"
                                            nuevoUsuario = ""
                                            nuevaContrasena = ""
                                            nuevoRol = Rol.USUARIO
                                        } else {
                                            mensaje = resultado
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Crear usuario")
                    }

// Cierra el Column interior
            }

// Cierra el ElevatedCard
        }

        if (mensaje.isNotEmpty()) {
            Text(mensaje)
        }

        HorizontalDivider()

        Text(
            "Usuarios registrados",
            style = MaterialTheme.typography.titleMedium
        )

        // Genera una tarjeta por cada usuario.
        usuarios.forEach { usuario ->

            key(usuario.nombre) {
                TarjetaUsuario(
                    usuario = usuario,
                    repositorio = repositorio,
                    actualizar = actualizar
                )
            }
        }
    }
}


// ============================================
// 15. TARJETA DE GESTIÓN DE USUARIO
// ============================================

@Composable
fun TarjetaUsuario(
    usuario: Cuenta,
    repositorio: RepositorioUsuarios,
    actualizar: () -> Unit
) {

    // Nombre editable de la cuenta.
    var nuevoNombre by remember(usuario.nombre) {
        mutableStateOf(usuario.nombre)
    }

    // Mensajes de cada operación.
    var mensaje by remember(usuario.nombre) {
        mutableStateOf("")
    }

    // Controla la confirmación de eliminación.
    var confirmarEliminacion by remember(usuario.nombre) {
        mutableStateOf(false)
    }

    // Identifica la cuenta protegida.
    val esAdminPrincipal =
        usuario.uid == FirebaseAuth.getInstance().currentUser?.uid

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Text(
                "Usuario: ${usuario.nombre}",
                style = MaterialTheme.typography.titleMedium
            )

            Text("Rol: ${usuario.rol.name}")

            Text(
                if (usuario.bloqueado) {
                    "Estado: BLOQUEADO"
                } else {
                    "Estado: ACTIVO"
                }
            )

            if (esAdminPrincipal) {

                // Protege al administrador principal.
                Text(
                    "Administrador principal protegido",
                    color = MaterialTheme.colorScheme.primary
                )

            } else {

                // Permite modificar el nombre.
                OutlinedTextField(
                    value = nuevoNombre,
                    onValueChange = {
                        nuevoNombre = it
                    },
                    label = {
                        Text("Modificar nombre")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {

                        val resultado =
                            repositorio.modificarNombre(
                                usuario.nombre,
                                nuevoNombre
                            )

                        if (resultado == null) {
                            mensaje = "Nombre modificado"
                            actualizar()
                        } else {
                            mensaje = resultado
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar nombre")
                }

                Text("Asignar rol")

                // Permite cambiar el rol.
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    FilterChip(
                        selected = usuario.rol == Rol.USUARIO,
                        onClick = {
                            RepositorioUsuariosFirebase.cambiarRol(
                                uid = usuario.uid,
                                nuevoRol = Rol.USUARIO
                            ) { resultado ->
                                mensaje = resultado ?: "Rol actualizado correctamente"
                            }
                        },
                        label = {
                            Text("Usuario")
                        }
                    )

                    FilterChip(
                        selected = usuario.rol == Rol.ADMINISTRADOR,
                        onClick = {
                            RepositorioUsuariosFirebase.cambiarRol(
                                uid = usuario.uid,
                                nuevoRol = Rol.ADMINISTRADOR
                            ) { resultado ->
                                mensaje = resultado ?: "Rol actualizado correctamente"
                            }
                        },
                        label = {
                            Text("Administrador")
                        }
                    )
                }

                // Bloquea o desbloquea la cuenta.
// Bloquear o desbloquear usuario en Firebase.
                OutlinedButton(
                    onClick = {
                        RepositorioUsuariosFirebase.cambiarEstado(
                            uid = usuario.uid,
                            activo = usuario.bloqueado
                        ) { resultado ->

                            mensaje = resultado
                                ?: if (usuario.bloqueado) {
                                    "Usuario desbloqueado correctamente"
                                } else {
                                    "Usuario bloqueado correctamente"
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (usuario.bloqueado) {
                            "Desbloquear usuario"
                        } else {
                            "Bloquear usuario"
                        }
                    )
                }

                // Solicita confirmar la eliminación.
                if (!confirmarEliminacion) {

                    OutlinedButton(
                        onClick = {
                            confirmarEliminacion = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Eliminar usuario")
                    }

                } else {

                    Text(
                        "¿Confirmas que deseas eliminar esta cuenta?",
                        color = MaterialTheme.colorScheme.error
                    )

                    Button(
                        onClick = {

                            val resultado =
                                repositorio.eliminarUsuario(
                                    usuario.nombre
                                )

                            if (resultado == null) {
                                actualizar()
                            } else {
                                mensaje = resultado
                            }

                            confirmarEliminacion = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Confirmar eliminación")
                    }

                    OutlinedButton(
                        onClick = {
                            confirmarEliminacion = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar")
                    }
                }
            }

            if (mensaje.isNotEmpty()) {
                Text(mensaje)
            }
        }
    }
}
