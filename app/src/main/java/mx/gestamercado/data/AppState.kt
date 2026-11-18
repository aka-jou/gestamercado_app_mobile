package mx.gestamercado.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import mx.gestamercado.api.AsistenciaRequest
import mx.gestamercado.api.CobroCampoRequest
import mx.gestamercado.api.CorteResultado
import mx.gestamercado.api.FeriaExpressRequest
import mx.gestamercado.api.Firma
import mx.gestamercado.api.FormaPago
import mx.gestamercado.api.GeoPoint
import mx.gestamercado.api.Jornada
import mx.gestamercado.api.LoginResponse
import mx.gestamercado.api.Perfil
import mx.gestamercado.api.Rutas
import mx.gestamercado.api.Terminal
import mx.gestamercado.api.TipoIncidencia
import mx.gestamercado.api.dinero
import mx.gestamercado.util.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.min
import kotlin.random.Random

enum class LocalStatus { AL_CORRIENTE, PENDIENTE, MOROSO, DESOCUPADO }
enum class SyncState { EN_COLA, SINCRONIZADO, REVISION }

val ZONA_MX: ZoneId = ZoneId.of("America/Mexico_City")

data class Suplente(val nombre: String, val parentesco: String, val iniciales: String)

class Local(
    val id: String,
    val nombre: String,
    val tarjeton: String,
    val zona: String,
    val titular: String,
    val giroAutorizado: String,
    val giroObservado: String,
    val tipo: String,
    val estatus: String,
    val concesion: String,
    val formal: Boolean,
    val base: LocalStatus,
    val area: Double,
    val areaCalculada: Double?,
    val suplentes: List<Suplente>,
    val x: Float,
    val y: Float,
    val concesionId: String? = null,
    val expediente: String? = null,
    val vigencia: String? = null,
    val curpTitular: String? = null,
    val rfcTitular: String? = null,
    val cuotaServidor: Double? = null,
    /** UUID del local en la API (localId de los contratos). id es la clave visible («A-08»). */
    val apiId: String = id,
) {
    val cuota: Double get() = cuotaServidor ?: cuotaDiaria(tipo, area)
    val esFeria: Boolean get() = tipo == "TEMPORAL"
}

class Cargo(
    val id: Int,
    val concepto: String,
    val periodo: String,
    val calculo: String,
    val total: Double,
    pagado: Double = 0.0,
    val origen: Int? = null,
    val alVuelo: Boolean = false,
    /** Fecha de inicio del cargo: se envía como fechaInicioCargo para amortización dirigida. */
    val inicio: LocalDate? = null,
) {
    var pagado by mutableStateOf(pagado)
    val saldo: Double get() = round2(total - pagado)
    val estado: String get() = if (saldo <= 0) "PAGADO" else if (pagado > 0) "PARCIAL" else "PENDIENTE"
}

data class Aplicacion(val concepto: String, val monto: Double, val resultado: String, val resta: Double)
data class Previa(val items: List<Aplicacion>, val favor: Double)

class Pago(
    val folio: String,
    val uuid: String,
    val hash: String,
    val localId: String,
    val formaPago: String,
    val canal: String,
    val pagador: String,
    val cobrador: String,
    val fecha: LocalDateTime,
    val monto: Double,
    val detalle: List<Aplicacion> = emptyList(),
    val gps: Boolean = true,
    val favor: Double = 0.0,
    val app: Boolean = false,
    sync: SyncState = SyncState.EN_COLA,
    revision: String? = null,
    val cancelado: Boolean = false,
    val motivo: String? = null,
    /** Cuerpo JSON exacto que se envía al sincronizar (cobro firmado o permiso de feria). */
    val cuerpo: Map<String, Any?>? = null,
    val endpoint: String? = null,
    val ubicacion: GeoPoint? = null,
    val dispositivoId: String? = null,
    val permiso: PermisoFeria? = null,
) {
    var sync by mutableStateOf(sync)
    var revision by mutableStateOf(revision)
}

/** M-I2 · registro de pase de lista en cola offline. */
class Asistencia(
    val id: String,
    val localId: String,
    val tipo: TipoIncidencia,
    val fecha: LocalDateTime,
    val ubicacion: GeoPoint,
    val cuerpo: Map<String, Any?>,
    sync: SyncState = SyncState.EN_COLA,
) {
    var sync by mutableStateOf(sync)
    var revision by mutableStateOf<String?>(null)
}

data class PermisoFeria(val localId: String, val titular: String, val giro: String, val inicio: LocalDate, val fin: LocalDate, val folio: String)

data class Corte(val id: String, val fecha: String, val secuencia: Int, val pagos: Int, val monto: Double, val liquidado: String)

/** Local vinculado al locatario: titular o suplente (M-L2). */
data class MiLocal(val localId: String, val relacion: String)

private data class Seed(val id: String, val nombre: String, val titular: String, val giro: String, val st: LocalStatus, val tipo: String = "FIJO", val area: Double = 6.0)

private val ZONAS = mapOf(
    "A" to "Pasillo A · Tortillas y masa",
    "B" to "Pasillo B · Abarrotes",
    "C" to "Pasillo C · Frutas y legumbres",
    "F" to "Zona de feria · Permisos temporales",
)

private val SEEDS = listOf(
    Seed("A-05", "Masa y Nixtamal Doña Tere", "Teresa Flores Ávila", "Masa y nixtamal", LocalStatus.AL_CORRIENTE),
    Seed("A-06", "Tortillas a Mano Lupita", "Guadalupe Sánchez Cruz", "Tortillería", LocalStatus.PENDIENTE),
    Seed("A-07", "Molino El Maizal", "Ramiro Juárez Peña", "Molino", LocalStatus.AL_CORRIENTE),
    Seed("A-08", "Tortillería La Esperanza", "María Guadalupe Hernández Ortiz", "Tortillería", LocalStatus.MOROSO),
    Seed("A-09", "Tamales Doña Rosa", "Rosa María Téllez Ruiz", "Alimentos preparados", LocalStatus.AL_CORRIENTE),
    Seed("A-10", "Local disponible", "—", "—", LocalStatus.DESOCUPADO),
    Seed("B-01", "Abarrotes La Güera", "Carmen Ortega Lima", "Abarrotes", LocalStatus.PENDIENTE),
    Seed("B-02", "Cremería San José", "José Antonio Ríos Vega", "Cremería", LocalStatus.AL_CORRIENTE),
    Seed("B-03", "Semillas El Granero", "Felipe Morales Díaz", "Semillas y granos", LocalStatus.AL_CORRIENTE),
    Seed("B-04", "Abarrotes Don Chuy", "Jesús Pérez Cabrera", "Abarrotes", LocalStatus.PENDIENTE),
    Seed("B-05", "Chiles Secos Mary", "María Elena Castro Gil", "Chiles secos", LocalStatus.MOROSO),
    Seed("B-06", "Dulces Típicos Lulú", "Lourdes Mendoza Paz", "Dulces", LocalStatus.AL_CORRIENTE, "SEMIFIJO", 3.0),
    Seed("C-01", "Frutas El Edén", "Arturo Gómez Salas", "Frutas", LocalStatus.AL_CORRIENTE),
    Seed("C-02", "Verduras Hnos. Ruiz", "Pedro Ruiz Campos", "Legumbres", LocalStatus.AL_CORRIENTE),
    Seed("C-03", "Legumbres Chela", "Graciela Núñez Ramos", "Legumbres", LocalStatus.PENDIENTE),
    Seed("C-04", "Frutería La Huerta", "Héctor Vargas León", "Frutas", LocalStatus.AL_CORRIENTE),
    Seed("C-05", "Jugos y Licuados Paty", "Patricia Aguilar Mora", "Jugos", LocalStatus.PENDIENTE, "SEMIFIJO", 2.5),
    Seed("C-06", "Verduras Don Beto", "Alberto Reyes Soto", "Legumbres", LocalStatus.MOROSO, "FIJO", 4.5),
    Seed("F-01", "Espacio de feria 1", "—", "—", LocalStatus.DESOCUPADO, "TEMPORAL", 4.0),
    Seed("F-02", "Espacio de feria 2", "—", "—", LocalStatus.DESOCUPADO, "TEMPORAL", 4.0),
    Seed("F-03", "Espacio de feria 3", "—", "—", LocalStatus.DESOCUPADO, "TEMPORAL", 4.0),
    Seed("F-04", "Espacio de feria 4", "—", "—", LocalStatus.DESOCUPADO, "TEMPORAL", 4.0),
)

/** Posición en el plano del mercado (6 locales por fila). */
fun posicionPlano(i: Int) = (22f + (i % 6) * 57f) to (48f + (i / 6) * 126f)

private fun buildLocales(): List<Local> = SEEDS.mapIndexed { i, s ->
    val a08 = s.id == "A-08"
    val des = s.st == LocalStatus.DESOCUPADO
    val (x, y) = posicionPlano(i)
    Local(
        id = s.id,
        nombre = s.nombre,
        tarjeton = if (a08) "T-0187" else "T-0${200 + i * 7}",
        zona = ZONAS.getValue(s.id.take(1)),
        titular = s.titular,
        giroAutorizado = s.giro,
        giroObservado = if (a08) "Venta de abarrotes" else s.giro,
        tipo = s.tipo,
        estatus = if (des) "DESOCUPADO" else "OCUPADO",
        concesion = if (des) "—" else "VIGENTE",
        formal = s.id != "B-05",
        base = s.st,
        area = s.area,
        areaCalculada = if (a08) 6.12 else null,
        // Suplentes: solo parentesco en primer grado (W6.1 · máximo 2)
        suplentes = when (s.id) {
            "A-08" -> listOf(Suplente("José Luis Hernández Ruiz", "HIJO", "JL"), Suplente("Daniela Ruiz Hernández", "HIJA", "DR"))
            "A-09" -> listOf(Suplente("María Guadalupe Hernández Ortiz", "HIJA", "MG"))
            "B-04" -> listOf(Suplente("Martha Pérez Cabrera", "HIJA", "MP"))
            else -> emptyList()
        },
        x = x,
        y = y,
        concesionId = if (des) null else "CON-${s.id.replace("-", "")}",
        expediente = if (a08) "DM/CON/2019/0187" else if (des) null else "DM/CON/2018/0${100 + i * 3}",
        vigencia = if (a08) "01 mar 2019 – 28 feb 2029" else if (des) null else "15 ene 2018 – 14 ene 2028",
        curpTitular = if (a08) "HEOG780412MPLRRD05" else null,
        rfcTitular = if (a08) "HEOG780412KJ3" else null,
        apiId = java.util.UUID.nameUUIDFromBytes("local:${s.id}".toByteArray()).toString(),
    )
}

private fun hist(
    folio: String, local: String, f: LocalDateTime, m: Double,
    forma: String = "EFECTIVO", canal: String = "CAMPO_INSPECTOR", sync: SyncState = SyncState.SINCRONIZADO,
    revision: String? = null, cancelado: Boolean = false, motivo: String? = null,
): Pago {
    val r = Random(folio.hashCode())
    return Pago(
        folio = folio, uuid = uuidOf(r), hash = hexOf(r, 64), localId = local, formaPago = forma, canal = canal,
        pagador = "Titular", cobrador = if (canal == "CAJA_POLITICA_FISCAL") "Caja MP-01" else "J. Ramírez · M01-A9F",
        fecha = f, monto = m, sync = sync, revision = revision, cancelado = cancelado, motivo = motivo,
    )
}

/** Terminal del modo simulado (en producción se captura al enrolarla desde la web, W5). */
val TERMINAL_DEMO = Terminal(
    dispositivoId = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    serieFolio = "M01A9F",
    secretoHmac = "4f1c2a9be07d35c86a1e9f04b2d7c3e5a8f6b1d0c9e2a7f4b3d8e1c6a5f09b72",
    alias = "Poniente 03",
    modelo = "XCover 7",
)

class AppState(val simulado: Boolean = true) {
    var online by mutableStateOf(!simulado)
    var printer by mutableStateOf(true)
    var lastSync by mutableStateOf(LocalDateTime.of(2026, 10, 14, 9, 12, 40))
    var inspTab by mutableIntStateOf(0)
    var locTab by mutableIntStateOf(0)
    private var reloj: LocalDateTime = LocalDateTime.of(2026, 10, 14, 11, 42, 8)
    private var cargoSeq = 4600

    /* ───────── Sesión (M-L1 / M-I1) ───────── */
    var sesion by mutableStateOf<LoginResponse?>(null)
    var mercadoId by mutableStateOf(1L)
    var jornada by mutableStateOf<Jornada?>(null)
    var terminal by mutableStateOf<Terminal?>(if (simulado) TERMINAL_DEMO else null)
    var consecutivo by mutableIntStateOf(452)
    var diasMaxAntiguedadCobro by mutableIntStateOf(15)
    var mercadoNombre by mutableStateOf("Mercado Poniente · MP-01")
    var ultimoCorte by mutableStateOf<CorteResultado?>(null)

    val usuarioId: String get() = sesion?.usuarioId ?: ""
    val perfil: Perfil? get() = sesion?.perfil
    val nombre: String get() = sesion?.nombre?.takeIf { it.isNotBlank() } ?: sesion?.username ?: ""
    val iniciales: String get() = nombre.split(" ", ".").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "GM" }
    val jornadaCerrada: Boolean get() = ultimoCorte != null

    val hoy: LocalDate get() = if (simulado) LocalDate.of(2026, 10, 14) else LocalDate.now(ZONA_MX)
    fun ahora(): LocalDateTime = if (simulado) reloj else LocalDateTime.now(ZONA_MX)
    fun instante(d: LocalDateTime): Instant = d.atZone(ZONA_MX).toInstant()

    fun iniciarSesion(r: LoginResponse) {
        sesion = r
        r.mercadoId?.let { mercadoId = it }
        jornada = null
        ultimoCorte = null
        inspTab = 0
        locTab = 0
    }

    fun cerrarSesion() {
        sesion = null
        jornada = null
    }

    /* ───────── Padrón ───────── */
    var locales: List<Local> by mutableStateOf(if (simulado) buildLocales() else emptyList())
        private set
    private val cargos = mutableStateMapOf<String, SnapshotStateList<Cargo>>().apply {
        if (simulado) locales.forEach { put(it.id, buildCargos(it).toMutableStateList()) }
    }
    private val saldoFavorServidor = mutableStateMapOf<String, Double>()
    val pagos: SnapshotStateList<Pago> = (if (simulado) listOf(
        hist("M01A9F-000451", "A-06", LocalDateTime.of(2026, 10, 14, 11, 30, 12), 17.36, sync = SyncState.EN_COLA),
        hist("M01A9F-000450", "C-03", LocalDateTime.of(2026, 10, 14, 11, 18, 40), 104.16, sync = SyncState.EN_COLA),
        hist("M01A9F-000447", "B-05", LocalDateTime.of(2026, 10, 14, 10, 51, 3), 52.08, sync = SyncState.REVISION, revision = "Diferencia de cálculo: servidor calcula \$69.44"),
        hist("M01A9F-000446", "A-05", LocalDateTime.of(2026, 10, 14, 10, 40, 55), 17.36),
        hist("M01A9F-000439", "C-06", LocalDateTime.of(2026, 10, 13, 18, 57, 20), 34.72, sync = SyncState.REVISION, revision = "Sincronizado después del corte CC-0918"),
        hist("M01A9F-000398", "A-08", LocalDateTime.of(2026, 9, 27, 12, 5, 0), 20.00),
        hist("CAJA-MP01-000112", "A-08", LocalDateTime.of(2026, 9, 15, 10, 20, 0), 468.72, forma = "TRANSFERENCIA", canal = "CAJA_POLITICA_FISCAL"),
        hist("M01A9F-000311", "A-08", LocalDateTime.of(2026, 9, 2, 11, 48, 0), 50.00, cancelado = true, motivo = "Folio duplicado al sincronizar"),
        hist("M01A9F-000270", "A-08", LocalDateTime.of(2026, 8, 18, 12, 31, 0), 520.80, forma = "TARJETA", canal = "CAMPO_ADMINISTRADOR"),
    ) else emptyList()).toMutableStateList()
    val asistencias = mutableStateListOf<Asistencia>()
    val actas = mutableStateMapOf("B-04" to 2)
    val permisos = mutableStateMapOf<String, PermisoFeria>()
    private val generadoHoy = mutableStateListOf<String>()
    private val pagadoHoy = mutableStateListOf<String>()

    val cortes = (if (simulado) listOf(
        Corte("CC-0918", "13 oct", 1, 21, 2845.00, "13 oct 19:05"),
        Corte("CC-0916", "12 oct", 2, 3, 210.00, "13 oct 08:40"),
        Corte("CC-0912", "12 oct", 1, 24, 3120.00, "12 oct 18:52"),
        Corte("CC-0905", "11 oct", 1, 17, 2460.00, "11 oct 18:47"),
    ) else emptyList()).toMutableStateList()

    /* ───────── Locatario (M-L2 … M-L4) ───────── */
    var misLocales by mutableStateOf(if (simulado) listOf(MiLocal("A-08", "TITULAR"), MiLocal("A-09", "SUPLENTE · HIJA")) else emptyList())
    var localLocatario by mutableStateOf("A-08")
    val miLocal: Local? get() = locales.firstOrNull { it.id == localLocatario }

    private fun buildCargos(l: Local): List<Cargo> {
        val c = l.cuota
        val uma = f2(umaMes(l.tipo, l.area))
        if (l.id == "A-08") return listOf(
            Cargo(4380, "DERECHO_PISO", "01 sep – 27 sep 2026", "$uma UMA/mes · UMA \$117.31 · ${money(c)} × 27 u · 6.00 m²", 468.72, 468.72, inicio = LocalDate.of(2026, 9, 1)),
            Cargo(4471, "DERECHO_PISO", "28 sep – 30 sep 2026", "$uma UMA/mes × \$117.31 × 12 / 365 = ${money(c)} × 3 u", 52.08, 20.0, inicio = LocalDate.of(2026, 9, 28)),
            Cargo(4479, "RECARGO", "Mora sobre cargo #4471", "1.25% mensual tras el día 15 · cargo_origen_id 4471", 0.65, origen = 4471, inicio = LocalDate.of(2026, 10, 16)),
            Cargo(4512, "DERECHO_PISO", "01 oct – 13 oct 2026", "$uma UMA/mes · UMA \$117.31 · ${money(c)} × 13 u · 6.00 m²", 225.68, inicio = LocalDate.of(2026, 10, 1)),
        )
        if (l.base == LocalStatus.MOROSO) {
            val n = if (l.id == "C-06") 7 else 13
            return listOf(
                Cargo(cargoSeq++, "DERECHO_PISO", if (n == 7) "07 oct – 13 oct 2026" else "01 oct – 13 oct 2026", "$uma UMA/mes · ${money(c)} × $n u · ${f2(l.area)} m²", round2(c * n), inicio = LocalDate.of(2026, 10, if (n == 7) 7 else 1)),
            )
        }
        return emptyList()
    }

    fun local(id: String): Local = locales.first { it.id == id }
    fun localOrNull(id: String): Local? = locales.firstOrNull { it.id == id }
    fun cargosDe(id: String): List<Cargo> = cargos[id] ?: emptyList()
    fun adeudo(id: String): Double = round2(cargosDe(id).sumOf { it.saldo })
    fun vencidos(id: String): Int = cargosDe(id).count { it.saldo > 0 && !it.alVuelo }
    fun saldoFavor(id: String): Double = saldoFavorServidor[id] ?: round2(pagos.filter { it.localId == id && !it.cancelado }.sumOf { it.favor })
    fun generadoHoy(id: String) = generadoHoy.contains(id)
    val pendientes: Int get() = pagos.count { it.sync == SyncState.EN_COLA } + asistencias.count { it.sync == SyncState.EN_COLA }
    fun pago(folio: String): Pago = pagos.first { it.folio == folio }
    fun pagosDe(id: String): List<Pago> = pagos.filter { it.localId == id }.sortedByDescending { it.fecha }
    val colaTurno: List<Pago> get() = pagos.filter { (it.canal == "CAMPO_INSPECTOR" || it.canal == "FERIA_EXPRESS") && it.fecha.isAfter(hoy.minusDays(1).atStartOfDay()) }.sortedByDescending { it.fecha }

    fun permisoVigente(id: String): PermisoFeria? = permisos[id]?.takeIf { !hoy.isAfter(it.fin) }

    fun statusDe(l: Local): LocalStatus = when {
        permisoVigente(l.id) != null -> LocalStatus.AL_CORRIENTE
        l.base == LocalStatus.DESOCUPADO -> LocalStatus.DESOCUPADO
        adeudo(l.id) > 0 -> LocalStatus.MOROSO
        l.base == LocalStatus.PENDIENTE && !pagadoHoy.contains(l.id) -> LocalStatus.PENDIENTE
        else -> LocalStatus.AL_CORRIENTE
    }

    /** Orden de aplicación: FIFO, salvo que se elija un cargo (fechaInicioCargo), que va primero. */
    private fun orden(lista: List<Cargo>, dirigido: LocalDate?): List<Cargo> =
        if (dirigido == null) lista else lista.sortedBy { if (it.inicio == dirigido) 0 else 1 }

    fun simular(id: String, monto: Double, unidades: Int, dirigido: LocalDate? = null): Previa {
        val l = local(id)
        val lineas = mutableListOf<Pair<String, Double>>()
        orden(cargosDe(id), dirigido).filter { it.saldo > 0 }.forEach { lineas += "${it.concepto} #${it.id}" to it.saldo }
        if (unidades > 0 && !generadoHoy(id)) lineas += "DERECHO_PISO hoy · $unidades u" to round2(l.cuota * unidades)
        var rest = round2(monto)
        val out = mutableListOf<Aplicacion>()
        for ((nombre, saldo) in lineas) {
            if (rest <= 0) {
                out += Aplicacion(nombre, 0.0, "PENDIENTE", saldo)
                continue
            }
            val ap = min(rest, saldo)
            rest = round2(rest - ap)
            out += Aplicacion(nombre, round2(ap), if (ap >= saldo) "PAGADO" else "PARCIAL", round2(saldo - ap))
        }
        return Previa(out, if (rest > 0) rest else 0.0)
    }

    private fun siguienteFolio(t: Terminal): String = Firma.folio(t.serieFolio, consecutivo).also { consecutivo++ }

    private fun cobrador(t: Terminal) = "${nombre.ifBlank { "Inspector" }} · ${t.serieFolio}"

    /** M-I3 · Cobro offline-first: genera pagoId, folio de la serie y firma HMAC-SHA256; queda en cola. */
    fun cobrar(local: Local, monto: Double, unidades: Int, forma: FormaPago, pagador: String, ubicacion: GeoPoint?, dirigido: LocalDate?): Pago {
        val t = terminal ?: error("La terminal no está enrolada.")
        val fecha = ahora()
        val req = CobroCampoRequest(
            pagoId = Firma.nuevoUuid(),
            localId = local.apiId,
            cobradorId = usuarioId,
            dispositivoId = t.dispositivoId,
            folio = Firma.folio(t.serieFolio, consecutivo),
            montoCobrado = dinero(monto),
            fecha = instante(fecha),
            formaPago = forma,
            ubicacion = ubicacion,
            unidades = BigDecimal(unidades),
            fechaInicioCargo = dirigido,
        ).firmado(t.secretoHmac)
        val errores = req.validar()
        check(errores.isEmpty()) { errores.joinToString(" ") }
        siguienteFolio(t)

        val previa = simular(local.id, monto, unidades, dirigido)
        val lista = cargos.getOrPut(local.id) { mutableStateListOf() }
        if (unidades > 0 && !generadoHoy(local.id)) {
            generadoHoy += local.id
            lista += Cargo(
                cargoSeq++, "DERECHO_PISO", "${fDia(hoy)} · $unidades u · al vuelo",
                "${f2(umaMes(local.tipo, local.area))} UMA/mes · ${money(local.cuota)} × $unidades u",
                round2(local.cuota * unidades), alVuelo = true, inicio = hoy,
            )
        }
        var rest = round2(monto)
        for (c in orden(lista, dirigido)) {
            if (rest <= 0) break
            if (c.saldo <= 0) continue
            val ap = min(rest, c.saldo)
            c.pagado = round2(c.pagado + ap)
            rest = round2(rest - ap)
        }
        if (!pagadoHoy.contains(local.id)) pagadoHoy += local.id
        val p = Pago(
            folio = req.folio, uuid = req.pagoId, hash = req.hashOffline,
            localId = local.id, formaPago = forma.name, canal = "CAMPO_INSPECTOR", pagador = pagador, cobrador = cobrador(t),
            fecha = fecha, monto = round2(monto), detalle = previa.items.filter { it.monto > 0 }, favor = previa.favor, app = true,
            gps = ubicacion != null, sync = SyncState.EN_COLA, cuerpo = req.toMap(), endpoint = Rutas.COBRO_CAMPO,
            ubicacion = ubicacion, dispositivoId = t.dispositivoId,
        )
        avanzarReloj()
        pagos.add(0, p)
        return p
    }

    /** M-I4 · Permiso temporal de feria cobrado en el acto. */
    fun expedirFeria(local: Local, titularId: String, titular: String, giroId: Int, giro: String, inicio: LocalDate, dias: Int, monto: Double, forma: FormaPago, ubicacion: GeoPoint?): Pago {
        val t = terminal ?: error("La terminal no está enrolada.")
        val req = FeriaExpressRequest(
            localId = local.apiId, titularPersonaId = titularId, giroId = giroId, fechaInicio = inicio, dias = dias,
            pagoId = Firma.nuevoUuid(), dispositivoId = t.dispositivoId, folio = Firma.folio(t.serieFolio, consecutivo),
            montoCobrado = dinero(monto), formaPago = forma, ubicacion = ubicacion,
        ).firmado(t.secretoHmac)
        val errores = req.validar()
        check(errores.isEmpty()) { errores.joinToString(" ") }
        siguienteFolio(t)
        val permiso = PermisoFeria(local.id, titular, giro, inicio, req.fechaFin, req.folio)
        permisos[local.id] = permiso
        val p = Pago(
            folio = req.folio, uuid = req.pagoId, hash = req.hashOffline, localId = local.id, formaPago = forma.name, canal = "FERIA_EXPRESS",
            pagador = "$titular · FERIANTE", cobrador = cobrador(t), fecha = ahora(), monto = round2(monto),
            detalle = listOf(Aplicacion("PERMISO_FERIA · $dias días · $giro", round2(monto), "PAGADO", 0.0)), app = true,
            gps = ubicacion != null, sync = SyncState.EN_COLA, cuerpo = req.toMap(), endpoint = Rutas.FERIA_EXPRESS,
            ubicacion = ubicacion, dispositivoId = t.dispositivoId, permiso = permiso,
        )
        avanzarReloj()
        pagos.add(0, p)
        return p
    }

    /** M-I2 · Pase de lista con GPS; se encola para POST /api/v1/asistencias. */
    fun registrarAsistencia(localId: String, tipo: TipoIncidencia, observaciones: String?, ubicacion: GeoPoint): Asistencia {
        val fecha = ahora()
        val req = AsistenciaRequest(local(localId).apiId, fecha.toLocalDate(), tipo, observaciones, ubicacion)
        val errores = req.validar()
        check(errores.isEmpty()) { errores.joinToString(" ") }
        val a = Asistencia(Firma.nuevoUuid(), localId, tipo, fecha, ubicacion, req.toMap())
        asistencias.add(0, a)
        if (tipo == TipoIncidencia.PRESENTE) { if (!pagadoHoy.contains(localId)) pagadoHoy += localId }
        else actas[localId] = (actas[localId] ?: 0) + 1
        avanzarReloj()
        return a
    }

    private fun avanzarReloj() { if (simulado) reloj = reloj.plusMinutes(4).plusSeconds(13) }

    /** Registros pendientes de envío, en orden cronológico. */
    fun colaDeEnvio(): List<Any> = (pagos.filter { it.sync == SyncState.EN_COLA }.map { it.fecha to it } + asistencias.filter { it.sync == SyncState.EN_COLA }.map { it.fecha to it })
        .sortedBy { it.first }.map { it.second }

    fun marcarSincronizado() { lastSync = ahora() }

    /** M-I5 · tras cerrar el corte ciego la jornada queda cerrada y se registra el corte. */
    fun registrarCorte(r: CorteResultado) {
        ultimoCorte = r
        cortes.add(0, Corte(r.id ?: "CC-${hoy.dayOfYear}", fDia(hoy).substringBeforeLast(" "), 1, turno.size, r.montoDeclarado, fCorta(ahora())))
    }

    /* ───────── Carga desde el backend real (data/Remoto.kt) ───────── */

    fun reemplazarPadron(nuevos: List<Local>) {
        locales = nuevos
        nuevos.forEach { if (!cargos.containsKey(it.id)) cargos[it.id] = mutableStateListOf() }
    }
    fun reemplazarCargos(localId: String, lista: List<Cargo>) { cargos[localId] = lista.toMutableStateList() }
    fun reemplazarSaldoFavor(localId: String, v: Double) { saldoFavorServidor[localId] = v }
    fun reemplazarPagos(localId: String, lista: List<Pago>) {
        pagos.removeAll { it.localId == localId && it.cuerpo == null }
        pagos.addAll(lista)
    }

    private val turno: List<Pago> get() = pagos.filter { it.app && !it.cancelado }
    private fun sum(forma: String) = turno.filter { it.formaPago == forma }.sumOf { it.monto }
    private fun n(forma: String) = turno.count { it.formaPago == forma }
    private val base get() = if (simulado) 1.0 else 0.0
    val efectivoTurno get() = round2(3212.50 * base + sum("EFECTIVO"))
    val cobrosEfectivo get() = (18 * base).toInt() + n("EFECTIVO")
    val transferTurno get() = round2(640.0 * base + sum("TRANSFERENCIA"))
    val cobrosTransfer get() = (3 * base).toInt() + n("TRANSFERENCIA")
    val tarjetaTurno get() = round2(280.0 * base + sum("TARJETA"))
    val cobrosTarjeta get() = (1 * base).toInt() + n("TARJETA")
    val cobrosTurno get() = cobrosEfectivo + cobrosTransfer + cobrosTarjeta
}
