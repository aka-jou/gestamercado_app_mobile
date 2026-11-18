package mx.gestamercado.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.gestamercado.api.Backend
import mx.gestamercado.api.Rutas
import mx.gestamercado.api.asList
import mx.gestamercado.api.asMap
import mx.gestamercado.api.num
import mx.gestamercado.api.str
import mx.gestamercado.util.fDia
import mx.gestamercado.util.round2
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Lecturas contra el backend real (solo cuando hay API_URL). La especificación fija los
 * endpoints pero no la forma de las respuestas, así que el mapeo acepta varios nombres de campo
 * y tolera los que falten. Ajusta aquí si el backend usa otros nombres.
 */
object Remoto {

    /** M-I2 · padrón del mercado para la ronda: GET /api/v1/locales?mercadoId={id} */
    suspend fun cargarPadron(app: AppState, backend: Backend) {
        if (backend.simulado) return
        val json = withContext(Dispatchers.IO) { backend.leer(Rutas.locales(app.mercadoId)) }
        app.reemplazarPadron(json.asList().mapIndexed { i, it -> local(it.asMap(), i) })
    }

    /** M-L2 · locales del locatario (como titular o suplente; RLS filtra en el servidor). */
    suspend fun cargarMisLocales(app: AppState, backend: Backend) {
        if (backend.simulado) return
        val lista = withContext(Dispatchers.IO) { backend.leer(Rutas.locales(null)) }.asList().map { it.asMap() }
        val locales = lista.mapIndexed { i, m -> local(m, i) }
        // Datos de la concesión de cada local: GET /api/v1/concesiones/local/{localId}
        val conConcesion = locales.map { l ->
            val c = runCatching { withContext(Dispatchers.IO) { backend.leer(Rutas.concesionesDeLocal(l.apiId)) } }.getOrNull()
                .asList().map { it.asMap() }.firstOrNull { (it.str("estado") ?: "VIGENTE") == "VIGENTE" }
            if (c == null) l else copiar(l, c)
        }
        app.reemplazarPadron(conConcesion)
        app.misLocales = lista.zip(conConcesion).map { (m, l) -> MiLocal(l.id, m.str("relacion", "rolLocatario", "vinculo") ?: "TITULAR") }
        app.misLocales.firstOrNull()?.let { if (app.misLocales.none { m -> m.localId == app.localLocatario }) app.localLocatario = it.localId }
    }

    /** M-L3 / M-L4 · adeudos, saldo a favor, cargos y pagos del local. */
    suspend fun cargarCuenta(app: AppState, backend: Backend, localId: String) {
        if (backend.simulado) return
        val l = app.localOrNull(localId) ?: return
        val cargos = withContext(Dispatchers.IO) { backend.leer(Rutas.cargos(l.apiId)) }.asList().map { cargo(it.asMap()) }
        app.reemplazarCargos(localId, cargos)
        val favor = withContext(Dispatchers.IO) { backend.leer(Rutas.saldoFavor(l.apiId)) }
        app.reemplazarSaldoFavor(localId, (favor as? Number)?.toDouble() ?: favor.asMap().num("saldoFavor", "saldo", "monto") ?: 0.0)
        val pagos = withContext(Dispatchers.IO) { backend.leer(Rutas.pagosDeLocal(l.apiId)) }.asList().map { pago(it.asMap(), localId) }
        app.reemplazarPagos(localId, pagos)
    }

    private fun fecha(s: String?): LocalDateTime? = s?.let {
        runCatching { LocalDateTime.ofInstant(Instant.parse(it), ZONA_MX) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(it) }.getOrNull()
            ?: runCatching { LocalDate.parse(it.take(10)).atStartOfDay() }.getOrNull()
    }

    private fun local(m: Map<String, Any?>, i: Int): Local {
        val apiId = m.str("id", "localId") ?: "local-$i"
        val clave = m.str("clave", "codigo", "numero") ?: apiId.take(8)
        val estatus = m.str("estatus", "estado") ?: "OCUPADO"
        val (x, y) = posicionPlano(i)
        val tipo = m.str("tipoEspacio", "tipo") ?: "FIJO"
        return Local(
            id = clave, nombre = m.str("nombre", "nombreComercial") ?: clave, tarjeton = m.str("tarjeton", "folioTarjeton") ?: "—",
            zona = m.str("zonaNombre") ?: m["zona"].asMap().str("nombre") ?: m.str("zona") ?: "—",
            titular = m.str("titularNombre", "titular") ?: m["titular"].asMap().str("nombre") ?: "—",
            giroAutorizado = m.str("giroNombre", "giro") ?: m["giro"].asMap().str("nombre") ?: "—",
            giroObservado = m.str("giroObservado", "giroNombre", "giro") ?: "—",
            tipo = tipo, estatus = estatus, concesion = m.str("estadoConcesion") ?: if (estatus == "OCUPADO") "VIGENTE" else "—",
            formal = (m["esFormal"] as? Boolean) ?: true,
            base = if (estatus == "OCUPADO") LocalStatus.AL_CORRIENTE else LocalStatus.DESOCUPADO,
            area = m.num("areaM2", "areaOficialM2", "area", "areaCalculadaM2") ?: 0.0, areaCalculada = m.num("areaCalculadaM2"),
            suplentes = emptyList(), x = x, y = y, concesionId = m.str("concesionId"), cuotaServidor = m.num("cuotaDiaria"), apiId = apiId,
        )
    }

    private fun copiar(l: Local, c: Map<String, Any?>) = Local(
        id = l.id, nombre = l.nombre, tarjeton = l.tarjeton, zona = l.zona, titular = c.str("titularNombre") ?: l.titular,
        giroAutorizado = c.str("giroNombre", "giro") ?: l.giroAutorizado, giroObservado = l.giroObservado, tipo = l.tipo, estatus = l.estatus,
        concesion = c.str("estado") ?: l.concesion, formal = (c["formal"] as? Boolean) ?: l.formal, base = l.base, area = l.area,
        areaCalculada = l.areaCalculada,
        suplentes = c["suplentes"].asList().map { s ->
            val x = s.asMap(); val n = x.str("nombre", "nombreCompleto") ?: "—"
            Suplente(n, x.str("parentesco") ?: "", n.split(" ").take(2).joinToString("") { it.take(1) })
        },
        x = l.x, y = l.y, concesionId = c.str("id", "concesionId") ?: l.concesionId, expediente = c.str("expediente", "numeroExpediente"),
        vigencia = listOfNotNull(c.str("fechaInicio", "inicio"), c.str("fechaFin", "vencimiento")).joinToString(" – ").ifBlank { null },
        curpTitular = c.str("titularCurp", "curp"), rfcTitular = c.str("titularRfc", "rfc"), cuotaServidor = l.cuotaServidor, apiId = l.apiId,
    )

    private fun cargo(m: Map<String, Any?>): Cargo {
        val total = m.num("monto", "total", "importe") ?: 0.0
        val saldo = m.num("saldo", "saldoPendiente")
        val inicio = fecha(m.str("fechaInicio", "periodoInicio"))?.toLocalDate()
        val fin = fecha(m.str("fechaFin", "periodoFin"))?.toLocalDate()
        return Cargo(
            id = m.num("id")?.toInt() ?: 0,
            concepto = m.str("concepto", "tipo") ?: "DERECHO_PISO",
            periodo = m.str("periodo") ?: listOfNotNull(inicio?.let { fDia(it) }, fin?.let { fDia(it) }).joinToString(" – "),
            calculo = m.str("descripcion", "calculo", "fundamento") ?: "",
            total = total,
            pagado = m.num("montoPagado", "pagado") ?: round2(total - (saldo ?: total)),
            origen = m.num("cargoOrigenId")?.toInt(),
            inicio = inicio,
        )
    }

    private fun pago(m: Map<String, Any?>, localId: String): Pago {
        val estado = m.str("estado", "estatus") ?: "VIGENTE"
        return Pago(
            folio = m.str("folio") ?: "—", uuid = m.str("id", "pagoId") ?: "", hash = m.str("hashOffline", "sello", "hash") ?: "",
            localId = localId, formaPago = m.str("formaPago") ?: "EFECTIVO", canal = m.str("canal") ?: "—",
            pagador = m.str("pagadorNombre", "pagador") ?: "Titular", cobrador = m.str("cobradorNombre", "cobrador") ?: "—",
            fecha = fecha(m.str("fecha", "fechaPago")) ?: LocalDateTime.now(ZONA_MX), monto = m.num("montoCobrado", "monto") ?: 0.0,
            favor = m.num("saldoFavorGenerado") ?: 0.0, sync = SyncState.SINCRONIZADO,
            cancelado = estado == "CANCELADO", motivo = m.str("motivoCancelacion", "motivo"),
        )
    }
}
