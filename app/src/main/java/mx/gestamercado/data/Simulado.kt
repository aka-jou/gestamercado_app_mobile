package mx.gestamercado.data

import mx.gestamercado.api.ApiError
import mx.gestamercado.api.Backend
import mx.gestamercado.api.CambiarPasswordRequest
import mx.gestamercado.api.CerrarCorteRequest
import mx.gestamercado.api.CorteResultado
import mx.gestamercado.api.Giro
import mx.gestamercado.api.Jornada
import mx.gestamercado.api.JornadaPendiente
import mx.gestamercado.api.LoginRequest
import mx.gestamercado.api.LoginResponse
import mx.gestamercado.api.PersonaBreve
import mx.gestamercado.api.Rutas
import mx.gestamercado.util.round2
import java.util.UUID

/** Cuentas de demostración del modo simulado (cualquier contraseña de 1+ caracteres). */
data class CuentaDemo(val username: String, val nombre: String, val roles: List<String>, val descripcion: String, val debeCambiarPass: Boolean = false)

val CUENTAS_DEMO = listOf(
    CuentaDemo("insp.jramirez", "Javier Ramírez Solís", listOf("INSPECTOR"), "Inspector · jornada libre"),
    CuentaDemo("insp.lmorales", "Luis Morales Pérez", listOf("INSPECTOR"), "Inspector · corte pendiente"),
    CuentaDemo("c.najera", "Carlos Nájera Ruiz", listOf("ADMINISTRADOR"), "Administrador de mercado · perfil campo"),
    CuentaDemo("loc.guadalupe.hernandez", "María Guadalupe Hernández Ortiz", listOf("LOCATARIO"), "Locatario · contraseña temporal", debeCambiarPass = true),
)

private fun uuidDe(s: String) = UUID.nameUUIDFromBytes(s.toByteArray()).toString()

/** Backend en memoria con las mismas reglas que la API: se usa cuando no hay API_URL. */
class Simulado(private val app: AppState) : Backend {
    override val simulado = true
    override var token: String? = null
    private val cambiadas = mutableMapOf<String, String>()

    private fun pausa(ms: Long = 450) = Thread.sleep(ms)
    private fun red() { if (!app.online) throw ApiError(0, "Sin conexión (modo simulado: toca OFFLINE para conectar).") }

    override fun login(r: LoginRequest): LoginResponse {
        pausa(600)
        val c = CUENTAS_DEMO.firstOrNull { it.username.equals(r.username.trim(), ignoreCase = true) }
            ?: throw ApiError(401, "Usuario o contraseña incorrectos.")
        if (r.password.isEmpty() || (cambiadas[c.username]?.let { it != r.password } == true)) throw ApiError(401, "Usuario o contraseña incorrectos.")
        token = "sim.${c.username}"
        return LoginResponse(
            token = token!!, usuarioId = uuidDe("usuario:${c.username}"), username = c.username, roles = c.roles,
            debeCambiarPass = c.debeCambiarPass && c.username !in cambiadas, nombre = c.nombre,
            personaId = if ("LOCATARIO" in c.roles) uuidDe("persona:${c.username}") else null, mercadoId = 1,
        )
    }

    override fun cambiarPassword(r: CambiarPasswordRequest) {
        pausa()
        val u = token?.removePrefix("sim.") ?: throw ApiError(401, "Sesión expirada.")
        val errores = r.validar()
        if (errores.isNotEmpty()) throw ApiError(422, errores.joinToString(" "))
        cambiadas[u] = r.passwordNueva
    }

    override fun puedeAbrirJornada(cobradorId: String, mercadoId: Long): Jornada {
        pausa(700)
        return if (token == "sim.insp.lmorales") Jornada(
            puedeAbrir = false,
            motivo = "Efectivo sin liquidar en Tesorería (RN-FIS-05).",
            pendientes = listOf(JornadaPendiente("05 oct 2026", 14, 1260.0, "Mercado Poniente · MP-01"), JornadaPendiente("06 oct 2026", 9, 810.0, "Mercado Poniente · MP-01")),
        ) else Jornada(true)
    }

    override fun enviar(ruta: String, cuerpo: Map<String, Any?>): Map<String, Any?> {
        red()
        pausa(150)
        return when (ruta) {
            Rutas.COBRO_CAMPO, Rutas.FERIA_EXPRESS -> mapOf("pagoId" to cuerpo["pagoId"], "estado" to "APLICADO")
            else -> mapOf("id" to UUID.randomUUID().toString())
        }
    }

    override fun buscarPersonas(q: String): List<PersonaBreve> {
        val t = q.trim().lowercase()
        return FERIANTES.filter { t.isEmpty() || it.nombre.lowercase().contains(t) || (it.curp ?: "").lowercase().contains(t) }
    }

    override fun giros() = GIROS

    override fun cerrarCorte(r: CerrarCorteRequest): CorteResultado {
        red()
        pausa(800)
        val esperado = app.efectivoTurno
        val declarado = r.montoDeclarado.toDouble()
        return CorteResultado("CC-0${919 + app.cortes.size - 4}", declarado, esperado, round2(declarado - esperado), "CERRADO")
    }

    override fun leer(ruta: String): Any? = null
    override fun cedulaPdf(concesionId: String): ByteArray? = null
    override fun reciboPdf(pagoId: String): ByteArray? = null

    companion object {
        val FERIANTES = listOf(
            PersonaBreve(uuidDe("feria:1"), "Rosa Elena Gómez Pérez", "GOPR850214MCSMRS04"),
            PersonaBreve(uuidDe("feria:2"), "Juan Carlos Díaz Molina", "DIMJ790921HCSZLN02"),
            PersonaBreve(uuidDe("feria:3"), "Artesanías Zoque S.C.", null),
            PersonaBreve(uuidDe("feria:4"), "Mónica Velázquez Ruiz", "VERM910305MCSLZN07"),
            PersonaBreve(uuidDe("feria:5"), "Pedro Antonio Hernández Gil", "HEGP700618HCSRLD01"),
        )
        val GIROS = listOf(
            Giro(1, "Artesanías"), Giro(2, "Alimentos preparados"), Giro(3, "Antojitos regionales"), Giro(4, "Dulces típicos"),
            Giro(5, "Ropa y calzado"), Giro(6, "Juguetes"), Giro(7, "Flores y plantas"), Giro(8, "Frutas de temporada"),
        )
    }
}
