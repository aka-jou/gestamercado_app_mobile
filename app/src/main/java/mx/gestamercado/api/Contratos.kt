package mx.gestamercado.api

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Contratos de la Parte 2 (Aplicación Móvil) de la «Especificación Técnica de Vistas, Campos y
 * Contratos». Los nombres de campo son exactamente los camelCase del documento.
 */

object Rutas {
    const val LOGIN = "/api/v1/auth/login"
    const val CAMBIAR_PASSWORD = "/api/v1/auth/cambiar-password"
    const val PUEDE_ABRIR_JORNADA = "/api/v1/cortes-caja/puede-abrir-jornada"
    const val ASISTENCIAS = "/api/v1/asistencias"
    const val COBRO_CAMPO = "/api/v1/cobranza/campo"
    const val FERIA_EXPRESS = "/api/v1/concesiones/feria-express"
    const val CERRAR_CORTE = "/api/v1/cortes-caja/cerrar"
    fun locales(mercadoId: Long?) = "/api/v1/locales" + (mercadoId?.let { "?mercadoId=$it" } ?: "")
    fun concesionesDeLocal(localId: String) = "/api/v1/concesiones/local/${enc(localId)}"
    fun cedulaPdf(concesionId: String) = "/api/v1/documentos/concesiones/${enc(concesionId)}/pdf"
    fun adeudos(localId: String) = "/api/v1/reportes-fiscales/adeudos/local/${enc(localId)}"
    fun saldoFavor(localId: String) = "/api/v1/reportes-fiscales/saldo-favor/local/${enc(localId)}"
    fun cargos(localId: String) = "/api/v1/cargos/local/${enc(localId)}"
    fun pagosDeLocal(localId: String) = "/api/v1/cobranza/local/${enc(localId)}"
    fun reciboPdf(pagoId: String) = "/api/v1/documentos/recibos/${enc(pagoId)}/pdf"
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** Montos en MXN con 2 decimales, serializados como número JSON. */
fun dinero(v: Double): BigDecimal = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP)

data class GeoPoint(val lon: Double, val lat: Double, val precisionM: Double? = null) {
    /** GeoJSON Point: { "type": "Point", "coordinates": [longitud, latitud] } */
    fun toMap(): Map<String, Any?> = mapOf("type" to "Point", "coordinates" to listOf(lon, lat))
    fun valida() = lon in -180.0..180.0 && lat in -90.0..90.0
}

enum class FormaPago { EFECTIVO, TRANSFERENCIA, TARJETA }
enum class TipoIncidencia { PRESENTE, AUSENTE_CERRADO, NEGATIVA_PAGO }

enum class Perfil { LOCATARIO, INSPECTOR }

/* ───────── M-L1 / M-I1 · Autenticación ───────── */

data class LoginRequest(val username: String, val password: String) {
    fun toMap() = mapOf("username" to username, "password" to password)
}

data class LoginResponse(
    val token: String,
    val usuarioId: String,
    val username: String,
    val roles: List<String>,
    val debeCambiarPass: Boolean,
    val nombre: String? = null,
    val personaId: String? = null,
    val mercadoId: Long? = null,
) {
    /** Perfil A si roles.includes("LOCATARIO"); perfil B si incluye INSPECTOR o ADMINISTRADOR. */
    val perfil: Perfil? get() = when {
        "LOCATARIO" in roles -> Perfil.LOCATARIO
        "INSPECTOR" in roles || "ADMINISTRADOR" in roles -> Perfil.INSPECTOR
        else -> null
    }

    companion object {
        fun from(j: Any?): LoginResponse {
            val m = j.asMap()
            val usuario = m["usuario"].asMap()
            val asignaciones = m["asignaciones"].asList().map { it.asMap() }
            return LoginResponse(
                token = m.str("token", "accessToken", "jwt") ?: throw ApiError(0, "La respuesta de login no trae token"),
                usuarioId = m.str("usuarioId", "id") ?: usuario.str("id") ?: "",
                username = m.str("username") ?: usuario.str("username") ?: "",
                roles = m["roles"].asList().map { r -> (r as? String) ?: r.asMap().str("clave", "nombre") ?: "" },
                debeCambiarPass = m.bool("debeCambiarPass") ?: usuario.bool("debeCambiarPass") ?: false,
                nombre = m.str("nombre", "nombreCompleto") ?: usuario.str("nombre"),
                personaId = m.str("personaId") ?: usuario.str("personaId"),
                mercadoId = (m.num("mercadoId") ?: asignaciones.firstNotNullOfOrNull { it.num("mercadoId") })?.toLong(),
            )
        }
    }
}

/** Nombres de campo supuestos: la especificación solo fija el endpoint. */
data class CambiarPasswordRequest(val passwordActual: String, val passwordNueva: String) {
    fun toMap() = mapOf("passwordActual" to passwordActual, "passwordNueva" to passwordNueva)
    fun validar(): List<String> = buildList {
        if (passwordNueva.length < 8) add("La nueva contraseña debe tener al menos 8 caracteres.")
        if (passwordNueva == passwordActual) add("La nueva contraseña debe ser distinta de la temporal.")
    }
}

/** Respuesta de GET /cortes-caja/puede-abrir-jornada (forma flexible). */
data class Jornada(val puedeAbrir: Boolean, val motivo: String? = null, val pendientes: List<JornadaPendiente> = emptyList()) {
    companion object {
        fun from(j: Any?): Jornada {
            if (j is Boolean) return Jornada(j)
            val m = j.asMap()
            return Jornada(
                puedeAbrir = m.bool("puedeAbrir", "puede", "permitido", "resultado") ?: false,
                motivo = m.str("motivo", "mensaje", "detail"),
                pendientes = (m["jornadasPendientes"] ?: m["pendientes"]).asList().map { p ->
                    val x = p.asMap()
                    JornadaPendiente(x.str("fecha", "fechaCorte") ?: "—", x.num("pagos", "numPagos")?.toInt() ?: 0, x.num("efectivo", "monto", "montoEfectivo") ?: 0.0, x.str("mercado", "mercadoNombre"))
                },
            )
        }
    }
}
data class JornadaPendiente(val fecha: String, val pagos: Int, val efectivo: Double, val mercado: String? = null)

/* ───────── M-I2 · Pase de lista GPS (POST /api/v1/asistencias) ───────── */

data class AsistenciaRequest(
    val localId: String,
    val fecha: LocalDate,
    val tipoIncidencia: TipoIncidencia,
    val observaciones: String?,
    val ubicacion: GeoPoint,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "localId" to localId,
        "fecha" to fecha.toString(),
        "tipoIncidencia" to tipoIncidencia.name,
        "observaciones" to observaciones?.trim()?.ifEmpty { null },
        "ubicacion" to ubicacion.toMap(),
    )
    fun validar(): List<String> = buildList {
        if (localId.isBlank()) add("Falta el local.")
        if (!ubicacion.valida()) add("Coordenada GPS inválida.")
    }
}

/* ───────── M-I3 · Cobro en campo offline (POST /api/v1/cobranza/campo) ───────── */

data class CobroCampoRequest(
    val pagoId: String,
    val localId: String,
    val cobradorId: String,
    val dispositivoId: String,
    val folio: String,
    val montoCobrado: BigDecimal,
    val fecha: Instant,
    val formaPago: FormaPago,
    val ubicacion: GeoPoint? = null,
    val unidades: BigDecimal? = null,
    val fechaInicioCargo: LocalDate? = null,
    val hashOffline: String = "",
) {
    /** Cadena canónica sobre la que se calcula la firma (ver Firma.canonicaCobro). */
    fun canonica() = Firma.canonicaCobro(this)
    fun firmado(secretoHmac: String) = copy(hashOffline = Firma.hmacSha256Hex(secretoHmac, canonica()))

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "pagoId" to pagoId,
        "localId" to localId,
        "cobradorId" to cobradorId,
        "dispositivoId" to dispositivoId,
        "folio" to folio,
        "montoCobrado" to montoCobrado,
        "fecha" to Firma.instante(fecha),
        "hashOffline" to hashOffline,
        "formaPago" to formaPago.name,
        "ubicacion" to ubicacion?.toMap(),
        "unidades" to unidades, // null → el servidor aplica el valor por defecto (1)
        "fechaInicioCargo" to fechaInicioCargo?.toString(),
    )

    fun validar(): List<String> = buildList {
        if (!Firma.esUuid(pagoId)) add("pagoId debe ser UUID v4.")
        if (localId.isBlank()) add("Falta el local.")
        if (cobradorId.isBlank()) add("Falta el inspector (cobradorId).")
        if (dispositivoId.isBlank()) add("La terminal no está enrolada (dispositivoId).")
        if (!Firma.RE_FOLIO.matches(folio)) add("Folio con formato inválido (SERIE-000001).")
        if (montoCobrado < BigDecimal("0.01")) add("El monto debe ser mayor a \$0.01.")
        if (!Firma.RE_HASH.matches(hashOffline)) add("Falta la firma HMAC-SHA256.")
        if (unidades != null && unidades.signum() < 0) add("Las unidades no pueden ser negativas.")
    }
}

/* ───────── M-I4 · Feria express (POST /api/v1/concesiones/feria-express) ───────── */

data class FeriaExpressRequest(
    val localId: String,
    val titularPersonaId: String,
    val giroId: Int,
    val fechaInicio: LocalDate,
    val dias: Int,
    val pagoId: String,
    val dispositivoId: String,
    val folio: String,
    val montoCobrado: BigDecimal,
    val formaPago: FormaPago = FormaPago.EFECTIVO,
    val ubicacion: GeoPoint? = null,
    val hashOffline: String = "",
) {
    val fechaFin: LocalDate get() = fechaInicio.plusDays((dias - 1).toLong())
    fun canonica() = Firma.canonicaFeria(this)
    fun firmado(secretoHmac: String) = copy(hashOffline = Firma.hmacSha256Hex(secretoHmac, canonica()))

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "localId" to localId,
        "titularPersonaId" to titularPersonaId,
        "giroId" to giroId,
        "fechaInicio" to fechaInicio.toString(),
        "dias" to dias,
        "pagoId" to pagoId,
        "dispositivoId" to dispositivoId,
        "folio" to folio,
        "montoCobrado" to montoCobrado,
        "hashOffline" to hashOffline,
        "formaPago" to formaPago.name,
        "ubicacion" to ubicacion?.toMap(),
    )

    fun validar(): List<String> = buildList {
        if (localId.isBlank()) add("Selecciona el espacio de feria.")
        if (titularPersonaId.isBlank()) add("Selecciona al comerciante.")
        if (giroId <= 0) add("Selecciona el giro.")
        if (dias !in 1..30) add("La duración debe ser de 1 a 30 días.")
        if (!Firma.esUuid(pagoId)) add("pagoId debe ser UUID v4.")
        if (dispositivoId.isBlank()) add("La terminal no está enrolada.")
        if (!Firma.RE_FOLIO.matches(folio)) add("Folio inválido.")
        if (montoCobrado < BigDecimal("0.01")) add("El monto debe ser mayor a \$0.01.")
        if (!Firma.RE_HASH.matches(hashOffline)) add("Falta la firma HMAC-SHA256.")
    }
}

/* ───────── M-I5 · Corte ciego (POST /api/v1/cortes-caja/cerrar) ───────── */

data class CerrarCorteRequest(
    val cobradorId: String,
    val mercadoId: Long,
    val fechaCorte: LocalDate,
    val montoDeclarado: BigDecimal,
    val observaciones: String?,
) {
    fun toMap(): Map<String, Any?> = linkedMapOf(
        "cobradorId" to cobradorId,
        "mercadoId" to mercadoId,
        "fechaCorte" to fechaCorte.toString(),
        "montoDeclarado" to montoDeclarado,
        "observaciones" to observaciones?.trim()?.ifEmpty { null },
    )
    fun validar(): List<String> = buildList {
        if (cobradorId.isBlank()) add("Falta el inspector.")
        if (montoDeclarado.signum() < 0) add("El efectivo declarado no puede ser negativo.")
    }
}

data class CorteResultado(val id: String?, val montoDeclarado: Double, val montoEsperado: Double?, val diferencia: Double?, val estado: String?) {
    companion object {
        fun from(j: Any?, declarado: Double): CorteResultado {
            val m = j.asMap()
            val esperado = m.num("montoEsperado", "montoSistema", "efectivoEsperado")
            return CorteResultado(
                id = m.str("id", "folio", "corteId"),
                montoDeclarado = m.num("montoDeclarado") ?: declarado,
                montoEsperado = esperado,
                diferencia = m.num("diferencia") ?: esperado?.let { declarado - it },
                estado = m.str("estado", "estatus"),
            )
        }
    }
}

/** Terminal POS enrolada desde la web (W5): id, prefijo de folios y secreto para firmar. */
data class Terminal(val dispositivoId: String, val serieFolio: String, val secretoHmac: String, val alias: String? = null, val modelo: String? = null) {
    fun validar(): List<String> = buildList {
        if (!Firma.esUuid(dispositivoId, soloV4 = false)) add("dispositivoId debe ser un UUID.")
        if (!Regex("^[A-Z0-9]{2,10}$").matches(serieFolio)) add("Prefijo de serie: 2 a 10 mayúsculas o números.")
        if (secretoHmac.length < 16) add("El secreto HMAC es demasiado corto.")
    }
}

/** Antigüedad máxima para sincronizar un cobro offline (municipio.diasMaxAntiguedadCobro). */
fun excedeAntiguedad(fechaCobro: Instant, ahora: Instant, diasMax: Int) = ChronoUnit.DAYS.between(fechaCobro, ahora) > diasMax
