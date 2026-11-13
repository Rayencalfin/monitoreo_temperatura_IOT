
package cl.inacap.monitoreoambientaliot

object SeguridadContrasenas {

    fun validar(contrasena: String): String? {

        if (contrasena.length < 8) {
            return "La contraseña debe tener mínimo 8 caracteres"
        }

        if (!contrasena.any { it.isUpperCase() }) {
            return "Debe incluir una letra mayúscula"
        }

        if (!contrasena.any { it.isLowerCase() }) {
            return "Debe incluir una letra minúscula"
        }

        if (!contrasena.any { it.isDigit() }) {
            return "Debe incluir un número"
        }

        if (!contrasena.any { !it.isLetterOrDigit() && !it.isWhitespace() }) {
            return "Debe incluir un carácter especial"
        }

        if (contrasena.any { it.isWhitespace() }) {
            return "No debe contener espacios"
        }

        val comunes = setOf(
            "12345678",
            "123456789",
            "password",
            "Password123!",
            "Admin123!",
            "Qwerty123!"
        )

        if (comunes.any { it.equals(contrasena, ignoreCase = true) }) {
            return "Esta contraseña es demasiado común"
        }

        return null
    }
}
