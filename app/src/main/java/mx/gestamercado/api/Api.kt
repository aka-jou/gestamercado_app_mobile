package mx.gestamercado.api

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

class ApiError(val status: Int, message: String) : Exception(message) {
    /** 0 = sin conexión: el registro se queda en la cola offline. */
    val sinRed get() = status == 0
    /** 409 al reenviar un pagoId ya recibido: el cobro ya está en el servidor (idempotencia). */
    val duplicado get() = status == 409
}

data class PersonaBreve(val id: String, val nombre: String, val curp: String? = null)
data class Giro(val id: Int, val nombre: String)

/**
 * Operaciones de la app móvil. Las llamadas son bloqueantes: invócalas desde Dispatchers.IO.
 * Implementaciones: [GestaApi] (REST real) y el backend simulado del paquete data.
 */
interface Backend {
    val simulado: Boolean
    var token: String?

    /* M-L1 / M-I1 */
    fun login(r: LoginRequest): LoginResponse
    fun cambiarPassword(r: CambiarPasswordRequest)
    fun puedeAbrirJornada(cobradorId: String, mercadoId: Long): Jornada

    /* Envío genérico de la cola offline (asistencias, cobros, ferias). */
    fun enviar(ruta: String, cuerpo: Map<String, Any?>): Map<String, Any?>

    /* M-I4 */
    fun buscarPersonas(q: String): List<PersonaBreve>
    fun giros(): List<Giro>

    /* M-I5 */
    fun cerrarCorte(r: CerrarCorteRequest): CorteResultado

    /* M-L2 … M-L4 (lecturas crudas; el mapeo a modelos vive en data.RemotoLocatario) */
    fun leer(ruta: String): Any?

    /* Documentos PDF con QR. null = generarlo localmente (modo simulado). */
    fun cedulaPdf(concesionId: String): ByteArray?
    fun reciboPdf(pagoId: String): ByteArray?
}

/** Cliente REST sin dependencias (HttpURLConnection) con autenticación Bearer JWT. */
class GestaApi(baseUrl: String, override var token: String? = null, private val timeoutMs: Int = 15_000) : Backend {
    private val base = baseUrl.trimEnd('/')
    override val simulado = false

    override fun login(r: LoginRequest): LoginResponse = LoginResponse.from(pedir("POST", Rutas.LOGIN, r.toMap(), auth = false)).also { token = it.token }
    override fun cambiarPassword(r: CambiarPasswordRequest) { pedir("POST", Rutas.CAMBIAR_PASSWORD, r.toMap()) }
    override fun puedeAbrirJornada(cobradorId: String, mercadoId: Long) =
        Jornada.from(pedir("GET", "${Rutas.PUEDE_ABRIR_JORNADA}?cobradorId=${q(cobradorId)}&mercadoId=$mercadoId"))
    override fun enviar(ruta: String, cuerpo: Map<String, Any?>) = pedir("POST", ruta, cuerpo).asMap()
    override fun buscarPersonas(q: String) = pedir("GET", "/api/v1/personas?q=${q(q)}&size=20").asList().map {
        val m = it.asMap()
        val nombre = m.str("nombreCompleto", "razonSocial") ?: listOfNotNull(m.str("nombres"), m.str("apellidoPaterno"), m.str("apellidoMaterno")).joinToString(" ")
        PersonaBreve(m.str("id") ?: "", nombre, m.str("curp"))
    }
    override fun giros() = pedir("GET", "/api/v1/giros").asList().map { val m = it.asMap(); Giro(m.num("id")?.toInt() ?: 0, m.str("nombre") ?: "") }
    override fun cerrarCorte(r: CerrarCorteRequest) = CorteResultado.from(pedir("POST", Rutas.CERRAR_CORTE, r.toMap()), r.montoDeclarado.toDouble())
    override fun leer(ruta: String) = pedir("GET", ruta)
    override fun cedulaPdf(concesionId: String) = bytes(Rutas.cedulaPdf(concesionId))
    override fun reciboPdf(pagoId: String) = bytes(Rutas.reciboPdf(pagoId))

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun abrir(metodo: String, ruta: String, auth: Boolean): HttpURLConnection =
        (URI(base + ruta).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = metodo
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            if (auth) token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }

    fun pedir(metodo: String, ruta: String, cuerpo: Map<String, Any?>? = null, auth: Boolean = true): Any? {
        val c = try { abrir(metodo, ruta, auth) } catch (e: IOException) { throw ApiError(0, "Sin conexión con el servidor") }
        try {
            if (cuerpo != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(Json.encode(cuerpo).toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val texto = (if (code >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            val json = if (texto.isBlank()) null else runCatching { Json.parse(texto) }.getOrElse { texto }
            if (code >= 400) throw ApiError(code, mensaje(json, code))
            return json
        } catch (e: IOException) {
            throw ApiError(0, "Sin conexión con el servidor")
        } finally {
            c.disconnect()
        }
    }

    private fun bytes(ruta: String): ByteArray {
        val c = try { abrir("GET", ruta, true).apply { setRequestProperty("Accept", "application/pdf") } } catch (e: IOException) { throw ApiError(0, "Sin conexión con el servidor") }
        try {
            val code = c.responseCode
            if (code >= 400) throw ApiError(code, mensaje(c.errorStream?.use { s -> runCatching { Json.parse(s.readBytes().toString(Charsets.UTF_8)) }.getOrNull() }, code))
            return c.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            throw ApiError(0, "Sin conexión con el servidor")
        } finally {
            c.disconnect()
        }
    }

    private fun mensaje(json: Any?, code: Int): String {
        val m = json.asMap()
        val campos = (m["errors"] ?: m["fieldErrors"]).asList().mapNotNull { e -> e.asMap().let { x -> x.str("defaultMessage", "message")?.let { msg -> x.str("field")?.let { "$it: $msg" } ?: msg } } }
        return listOfNotNull(m.str("detail", "message", "mensaje", "title") ?: (json as? String)?.take(200), campos.joinToString("; ").ifBlank { null })
            .joinToString(" · ").ifBlank { "Error $code del servidor" }
    }
}
