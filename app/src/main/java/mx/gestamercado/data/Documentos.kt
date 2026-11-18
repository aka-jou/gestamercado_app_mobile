package mx.gestamercado.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.gestamercado.api.Backend
import mx.gestamercado.util.f2
import mx.gestamercado.util.fFecha
import mx.gestamercado.util.money
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Documentos PDF con QR del locatario (M-L2 cédula de concesión, M-L4 recibo oficial).
 * Con backend real se descargan de /api/v1/documentos/…/pdf; en modo simulado se generan aquí
 * con el mismo contenido para poder probar el flujo de apertura y compartido.
 */
object Documentos {
    private const val TINTA = 0xFF121517.toInt()
    private const val MAGENTA = 0xFFC80766.toInt()
    private const val TEAL = 0xFF0F6B60.toInt()
    private const val GRIS = 0xFF6E7B78.toInt()

    /** M-L4 · GET /api/v1/documentos/recibos/{pagoId}/pdf (o PDF local si aún no se sincroniza). */
    suspend fun recibo(ctx: Context, app: AppState, backend: Backend, p: Pago, compartir: Boolean = false) {
        val remoto = if (backend.simulado || p.sync != SyncState.SINCRONIZADO) null else withContext(Dispatchers.IO) { backend.reciboPdf(p.uuid) }
        val pdf = remoto ?: withContext(Dispatchers.Default) { reciboSimulado(p, app.localOrNull(p.localId)) }
        abrir(ctx, "recibo_${p.folio}.pdf", pdf, compartir)
    }

    /** M-L2 · GET /api/v1/documentos/concesiones/{concesionId}/pdf */
    suspend fun cedula(ctx: Context, backend: Backend, l: Local, compartir: Boolean = false) {
        val id = l.concesionId ?: error("El local no tiene concesión vigente.")
        val pdf = withContext(Dispatchers.IO) { backend.cedulaPdf(id) } ?: withContext(Dispatchers.Default) { cedulaSimulada(l) }
        abrir(ctx, "cedula_${id}.pdf", pdf, compartir)
    }

    fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Guarda el PDF en caché y lo abre con el visor del sistema (o el selector para compartir). */
    fun abrir(ctx: Context, nombre: String, pdf: ByteArray, compartir: Boolean = false) {
        val dir = File(ctx.cacheDir, "documentos").apply { mkdirs() }
        val f = File(dir, nombre).apply { writeBytes(pdf) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.documentos", f)
        val ver = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val enviar = Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            nombre,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(if (compartir) enviar else ver) } catch (e: ActivityNotFoundException) { ctx.startActivity(enviar) }
    }

    fun cedulaSimulada(l: Local): ByteArray {
        val sello = sha256("${l.concesionId}|${l.id}|${l.titular}|${l.expediente}")
        return pagina("CÉDULA DE CONCESIÓN", l.concesionId ?: "—", "GM-CEDULA|${l.concesionId}|${l.id}|$sello", sello) { y0 ->
            var y = y0
            y = fila(this, "Local", "${l.id} · ${l.nombre}", y)
            y = fila(this, "Zona", l.zona, y)
            y = fila(this, "Tipo · superficie", "${l.tipo} · ${f2(l.area)} m²", y)
            y = fila(this, "Titular", l.titular, y)
            l.curpTitular?.let { y = fila(this, "CURP", it, y, mono = true) }
            l.rfcTitular?.let { y = fila(this, "RFC", it, y, mono = true) }
            y = fila(this, "Giro autorizado", l.giroAutorizado, y)
            y = fila(this, "Expediente", l.expediente ?: "—", y, mono = true)
            y = fila(this, "Vigencia", l.vigencia ?: "—", y)
            y = fila(this, "Carácter", if (l.formal) "Formal" else "Informal", y)
            y = fila(this, "Suplentes", if (l.suplentes.isEmpty()) "Sin suplentes" else l.suplentes.joinToString("; ") { "${it.nombre} (${it.parentesco})" }, y)
            y
        }
    }

    fun reciboSimulado(p: Pago, l: Local?): ByteArray =
        pagina(if (p.cancelado) "RECIBO OFICIAL · CANCELADO" else "RECIBO OFICIAL DE PAGO", p.folio, "${p.uuid}|${p.folio}|${f2(p.monto)}|${p.hash}", p.hash) { y0 ->
            var y = y0
            y = fila(this, "Folio", p.folio, y, mono = true)
            y = fila(this, "pagoId", p.uuid, y, mono = true)
            y = fila(this, "Fecha", fFecha(p.fecha), y)
            y = fila(this, "Local", l?.let { "${it.id} · ${it.nombre}" } ?: p.localId, y)
            l?.let { y = fila(this, "Titular", it.titular, y) }
            y = fila(this, "Pagó", p.pagador.substringBefore(" · "), y)
            y = fila(this, "Cobró", p.cobrador, y)
            y = fila(this, "Canal · forma", "${p.canal} · ${p.formaPago}", y)
            y += 8f
            (if (p.detalle.isEmpty()) listOf(Aplicacion("Pago aplicado por FIFO", p.monto, "PAGADO", 0.0)) else p.detalle).forEach { a ->
                y = fila(this, a.concepto + if (a.resultado == "PARCIAL") " (parcial)" else "", money(a.monto), y, mono = true)
            }
            if (p.favor > 0) y = fila(this, "Saldo a favor", money(p.favor), y, mono = true)
            y += 6f
            drawLine(48f, y, 547f, y, Paint().apply { color = TINTA; strokeWidth = 1.5f })
            y += 22f
            drawText("TOTAL", 48f, y, paint(13f, bold = true))
            drawText(money(p.monto), 547f - paint(16f, mono = true).measureText(money(p.monto)), y, paint(16f, mono = true))
            p.motivo?.let { y += 22f; drawText("Motivo de cancelación: $it", 48f, y, paint(10.5f, color = MAGENTA)) }
            y
        }

    private fun pagina(titulo: String, folio: String, qr: String, sello: String, cuerpo: Canvas.(Float) -> Float): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()) // A4 en puntos
        val c = page.canvas
        c.drawRect(0f, 0f, 595f, 92f, Paint().apply { color = TINTA })
        c.drawRect(0f, 92f, 595f, 98f, Paint().apply { color = MAGENTA })
        c.drawText("gesta mercado", 48f, 44f, paint(20f, bold = true, color = Color.WHITE))
        c.drawText("H. AYUNTAMIENTO · DIRECCIÓN DE MERCADOS", 48f, 66f, paint(9f, color = 0xFFA7B3B0.toInt()))
        c.drawText(titulo, 48f, 136f, paint(18f, bold = true))
        c.drawText(folio, 48f, 156f, paint(11f, mono = true, color = GRIS))
        val fin = c.cuerpo(190f)
        val y = maxOf(fin + 30f, 560f)
        dibujarQr(c, qr, 48f, y, 150f)
        c.drawText("SELLO SHA-256", 216f, y + 14f, paint(9f, bold = true, color = GRIS))
        sello.chunked(32).forEachIndexed { i, s -> c.drawText(s, 216f, y + 32f + i * 14f, paint(10f, mono = true)) }
        c.drawText("Autenticidad verificable al escanear el código QR.", 216f, y + 84f, paint(10f, color = TEAL))
        c.drawText("Documento generado por la app Gesta Mercado.", 48f, 810f, paint(8.5f, color = GRIS))
        doc.finishPage(page)
        return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }

    private fun fila(c: Canvas, k: String, v: String, y: Float, mono: Boolean = false): Float {
        c.drawText(k.uppercase(), 48f, y, paint(8.5f, bold = true, color = GRIS))
        val p = paint(11f, mono = mono)
        var yy = y
        // Ajuste de línea simple para valores largos
        val palabras = v.split(" ")
        var linea = ""
        for (w in palabras) {
            val prueba = if (linea.isEmpty()) w else "$linea $w"
            if (p.measureText(prueba) > 360f && linea.isNotEmpty()) { c.drawText(linea, 187f, yy, p); yy += 15f; linea = w } else linea = prueba
        }
        c.drawText(linea, 187f, yy, p)
        c.drawLine(48f, yy + 8f, 547f, yy + 8f, Paint().apply { color = 0xFFEEEAE3.toInt(); strokeWidth = 1f })
        return yy + 26f
    }

    private fun paint(size: Float, bold: Boolean = false, mono: Boolean = false, color: Int = TINTA) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (mono) Typeface.MONOSPACE else Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun dibujarQr(c: Canvas, data: String, x: Float, y: Float, lado: Float) {
        val m = QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val celda = lado / m.width
        val p = Paint().apply { color = TINTA }
        for (j in 0 until m.height) for (i in 0 until m.width) if (m.get(i, j)) c.drawRect(x + i * celda, y + j * celda, x + (i + 1) * celda, y + (j + 1) * celda, p)
    }
}
