package mx.gestamercado.api

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Firma HMAC-SHA256 de los cobros offline (hashOffline).
 *
 * La especificación pide firmar «la cadena canónica del cobro» con el secreto_hmac del
 * dispositivo, pero no fija el formato. Se usa el siguiente, que debe replicarse igual en el
 * backend al verificar:
 *
 *   cobro:  pagoId|localId|cobradorId|dispositivoId|folio|montoCobrado|fecha|formaPago
 *   feria:  pagoId|localId|titularPersonaId|giroId|fechaInicio|dias|dispositivoId|folio|montoCobrado|formaPago
 *
 * montoCobrado con 2 decimales y punto ("150.00"); fecha en ISO-8601 UTC con segundos
 * ("2026-11-16T15:30:00Z"). Si el secreto son 64 caracteres hexadecimales se usan sus 32 bytes;
 * en otro caso, sus bytes UTF-8.
 */
object Firma {
    val RE_HASH = Regex("^[0-9a-f]{64}$")
    val RE_FOLIO = Regex("^[A-Z0-9]{2,10}-\\d{6}$")
    private val RE_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val RE_HEX = Regex("^[0-9a-fA-F]{64}$")

    fun nuevoUuid(): String = UUID.randomUUID().toString()
    fun esUuid(s: String, soloV4: Boolean = true) = RE_UUID.matches(s) && (!soloV4 || s[14] == '4')

    /** SERIE-000001 */
    fun folio(serie: String, consecutivo: Int) = "$serie-${consecutivo.toString().padStart(6, '0')}"

    fun instante(i: Instant): String = DateTimeFormatter.ISO_INSTANT.format(i.truncatedTo(ChronoUnit.SECONDS))

    fun clave(secreto: String): ByteArray =
        if (RE_HEX.matches(secreto)) ByteArray(32) { secreto.substring(it * 2, it * 2 + 2).toInt(16).toByte() } else secreto.toByteArray(Charsets.UTF_8)

    fun hmacSha256Hex(secreto: String, mensaje: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(clave(secreto), "HmacSHA256"))
        return mac.doFinal(mensaje.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun canonicaCobro(r: CobroCampoRequest) = listOf(
        r.pagoId, r.localId, r.cobradorId, r.dispositivoId, r.folio, r.montoCobrado.toPlainString(), instante(r.fecha), r.formaPago.name,
    ).joinToString("|")

    fun canonicaFeria(r: FeriaExpressRequest) = listOf(
        r.pagoId, r.localId, r.titularPersonaId, r.giroId.toString(), r.fechaInicio.toString(), r.dias.toString(),
        r.dispositivoId, r.folio, r.montoCobrado.toPlainString(), r.formaPago.name,
    ).joinToString("|")

    fun verificar(secreto: String, canonica: String, hash: String) = hmacSha256Hex(secreto, canonica).equals(hash, ignoreCase = true)
}
