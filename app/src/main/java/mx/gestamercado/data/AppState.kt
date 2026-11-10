package mx.gestamercado.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import mx.gestamercado.util.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.random.Random

enum class LocalStatus { AL_CORRIENTE, PENDIENTE, MOROSO, DESOCUPADO }
enum class SyncState { EN_COLA, SINCRONIZADO, REVISION }

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
) {
    val cuota: Double get() = cuotaDiaria(tipo, area)
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
    val revision: String? = null,
    val cancelado: Boolean = false,
    val motivo: String? = null,
) {
    var sync by mutableStateOf(sync)
}

data class Corte(val id: String, val fecha: String, val secuencia: Int, val pagos: Int, val monto: Double, val liquidado: String)

private data class Seed(val id: String, val nombre: String, val titular: String, val giro: String, val st: LocalStatus, val tipo: String = "FIJO", val area: Double = 6.0)

private val ZONAS = mapOf(
    "A" to "Pasillo A · Tortillas y masa",
    "B" to "Pasillo B · Abarrotes",
    "C" to "Pasillo C · Frutas y legumbres",
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
)

private fun buildLocales(): List<Local> = SEEDS.mapIndexed { i, s ->
    val a08 = s.id == "A-08"
    val des = s.st == LocalStatus.DESOCUPADO
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
        suplentes = when (s.id) {
            "A-08" -> listOf(Suplente("José Luis Hernández Ortiz", "HERMANO", "JL"), Suplente("Daniela Ruiz Hernández", "HIJA", "DR"))
            "B-04" -> listOf(Suplente("Martha Cabrera Ruiz", "CONYUGE", "MC"))
            else -> emptyList()
        },
        x = 22f + (i % 6) * 57f,
        y = 48f + (i / 6) * 126f,
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

class AppState {
    var online by mutableStateOf(false)
    var printer by mutableStateOf(true)
    var inspector by mutableStateOf("insp.jramirez")
    var lastSync by mutableStateOf(LocalDateTime.of(2026, 10, 14, 9, 12, 40))
    var inspTab by mutableIntStateOf(0)
    var locTab by mutableIntStateOf(0)
    val hoy: LocalDate = LocalDate.of(2026, 10, 14)
    private var reloj: LocalDateTime = LocalDateTime.of(2026, 10, 14, 11, 42, 8)
    private var folio = 452
    private var cargoSeq = 4600

    val locales: List<Local> = buildLocales()
    private val cargos: Map<String, SnapshotStateList<Cargo>> = locales.associate { it.id to buildCargos(it).toMutableStateList() }
    val pagos: SnapshotStateList<Pago> = listOf(
        hist("M01A9F-000451", "A-06", LocalDateTime.of(2026, 10, 14, 11, 30, 12), 17.36, sync = SyncState.EN_COLA),
        hist("M01A9F-000450", "C-03", LocalDateTime.of(2026, 10, 14, 11, 18, 40), 104.16, sync = SyncState.EN_COLA),
        hist("M01A9F-000447", "B-05", LocalDateTime.of(2026, 10, 14, 10, 51, 3), 52.08, sync = SyncState.REVISION, revision = "Diferencia de cálculo: servidor calcula \$69.44"),
        hist("M01A9F-000446", "A-05", LocalDateTime.of(2026, 10, 14, 10, 40, 55), 17.36),
        hist("M01A9F-000439", "C-06", LocalDateTime.of(2026, 10, 13, 18, 57, 20), 34.72, sync = SyncState.REVISION, revision = "Sincronizado después del corte CC-0918"),
        hist("M01A9F-000398", "A-08", LocalDateTime.of(2026, 9, 27, 12, 5, 0), 20.00),
        hist("CAJA-MP01-000112", "A-08", LocalDateTime.of(2026, 9, 15, 10, 20, 0), 468.72, forma = "TRANSFERENCIA", canal = "CAJA_POLITICA_FISCAL"),
        hist("M01A9F-000311", "A-08", LocalDateTime.of(2026, 9, 2, 11, 48, 0), 50.00, cancelado = true, motivo = "Folio duplicado al sincronizar"),
        hist("M01A9F-000270", "A-08", LocalDateTime.of(2026, 8, 18, 12, 31, 0), 520.80, forma = "TARJETA", canal = "CAMPO_ADMINISTRADOR"),
    ).toMutableStateList()
    val actas = mutableStateMapOf("B-04" to 2)
    private val generadoHoy = mutableStateListOf<String>()
    private val pagadoHoy = mutableStateListOf<String>()

    val cortes = listOf(
        Corte("CC-0918", "13 oct", 1, 21, 2845.00, "13 oct 19:05"),
        Corte("CC-0916", "12 oct", 2, 3, 210.00, "13 oct 08:40"),
        Corte("CC-0912", "12 oct", 1, 24, 3120.00, "12 oct 18:52"),
        Corte("CC-0905", "11 oct", 1, 17, 2460.00, "11 oct 18:47"),
    )

    private fun buildCargos(l: Local): List<Cargo> {
        val c = l.cuota
        val uma = f2(umaMes(l.tipo, l.area))
        if (l.id == "A-08") return listOf(
            Cargo(4380, "DERECHO_PISO", "01 sep – 27 sep 2026", "$uma UMA/mes · UMA \$117.31 · ${money(c)} × 27 u · 6.00 m²", 468.72, 468.72),
            Cargo(4471, "DERECHO_PISO", "28 sep – 30 sep 2026", "$uma UMA/mes × \$117.31 × 12 / 365 = ${money(c)} × 3 u", 52.08, 20.0),
            Cargo(4479, "RECARGO", "Mora sobre cargo #4471", "1.25% mensual tras el día 15 · cargo_origen_id 4471", 0.65, origen = 4471),
            Cargo(4512, "DERECHO_PISO", "01 oct – 13 oct 2026", "$uma UMA/mes · UMA \$117.31 · ${money(c)} × 13 u · 6.00 m²", 225.68),
        )
        if (l.base == LocalStatus.MOROSO) {
            val n = if (l.id == "C-06") 7 else 13
            return listOf(
                Cargo(cargoSeq++, "DERECHO_PISO", if (n == 7) "07 oct – 13 oct 2026" else "01 oct – 13 oct 2026", "$uma UMA/mes · ${money(c)} × $n u · ${f2(l.area)} m²", round2(c * n)),
            )
        }
        return emptyList()
    }

    fun local(id: String): Local = locales.first { it.id == id }
    fun cargosDe(id: String): List<Cargo> = cargos[id] ?: emptyList()
    fun adeudo(id: String): Double = round2(cargosDe(id).sumOf { it.saldo })
    fun vencidos(id: String): Int = cargosDe(id).count { it.saldo > 0 && !it.alVuelo }
    fun generadoHoy(id: String) = generadoHoy.contains(id)
    val pendientes: Int get() = pagos.count { it.sync == SyncState.EN_COLA }
    fun pago(folio: String): Pago = pagos.first { it.folio == folio }
    fun pagosDe(id: String): List<Pago> = pagos.filter { it.localId == id }.sortedByDescending { it.fecha }
    val colaTurno: List<Pago> get() = pagos.filter { it.canal == "CAMPO_INSPECTOR" && it.fecha.isAfter(LocalDateTime.of(2026, 10, 13, 0, 0)) }.sortedByDescending { it.fecha }

    fun statusDe(l: Local): LocalStatus = when {
        l.base == LocalStatus.DESOCUPADO -> LocalStatus.DESOCUPADO
        adeudo(l.id) > 0 -> LocalStatus.MOROSO
        l.base == LocalStatus.PENDIENTE && !pagadoHoy.contains(l.id) -> LocalStatus.PENDIENTE
        else -> LocalStatus.AL_CORRIENTE
    }

    fun simular(id: String, monto: Double, unidades: Int): Previa {
        val l = local(id)
        val lineas = mutableListOf<Pair<String, Double>>()
        cargosDe(id).filter { it.saldo > 0 }.forEach { lineas += "${it.concepto} #${it.id}" to it.saldo }
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

    fun cobrar(local: Local, monto: Double, unidades: Int, forma: String, pagador: String): Pago {
        val previa = simular(local.id, monto, unidades)
        val lista = cargos.getValue(local.id)
        if (unidades > 0 && !generadoHoy(local.id)) {
            generadoHoy += local.id
            lista += Cargo(
                cargoSeq++, "DERECHO_PISO", "${fDia(hoy)} · $unidades u · al vuelo",
                "${f2(umaMes(local.tipo, local.area))} UMA/mes · ${money(local.cuota)} × $unidades u",
                round2(local.cuota * unidades), alVuelo = true,
            )
        }
        var rest = round2(monto)
        for (c in lista) {
            if (rest <= 0) break
            if (c.saldo <= 0) continue
            val ap = min(rest, c.saldo)
            c.pagado = round2(c.pagado + ap)
            rest = round2(rest - ap)
        }
        if (!pagadoHoy.contains(local.id)) pagadoHoy += local.id
        val r = Random(folio * 31 + monto.toInt())
        val p = Pago(
            folio = "M01A9F-${folio.toString().padStart(6, '0')}", uuid = uuidOf(r), hash = hexOf(r, 64),
            localId = local.id, formaPago = forma, canal = "CAMPO_INSPECTOR", pagador = pagador, cobrador = "J. Ramírez · M01-A9F",
            fecha = reloj, monto = round2(monto), detalle = previa.items.filter { it.monto > 0 }, favor = previa.favor, app = true,
            sync = if (online) SyncState.SINCRONIZADO else SyncState.EN_COLA,
        )
        folio++
        reloj = reloj.plusMinutes(4).plusSeconds(13)
        pagos.add(0, p)
        if (online) lastSync = p.fecha
        return p
    }

    fun sincronizar(): Int {
        if (!online) return -1
        var n = 0
        pagos.forEach { if (it.sync == SyncState.EN_COLA) { it.sync = SyncState.SINCRONIZADO; n++ } }
        lastSync = reloj
        return n
    }

    fun registrarActa(id: String): Int {
        val n = (actas[id] ?: 0) + 1
        actas[id] = n
        return n
    }

    fun registrarAsistencia(id: String) {
        if (!pagadoHoy.contains(id)) pagadoHoy += id
    }

    private val turno: List<Pago> get() = pagos.filter { it.app && !it.cancelado }
    private fun sum(forma: String) = turno.filter { it.formaPago == forma }.sumOf { it.monto }
    private fun n(forma: String) = turno.count { it.formaPago == forma }
    val efectivoTurno get() = round2(3212.50 + sum("EFECTIVO"))
    val cobrosEfectivo get() = 18 + n("EFECTIVO")
    val transferTurno get() = round2(640.0 + sum("TRANSFERENCIA"))
    val cobrosTransfer get() = 3 + n("TRANSFERENCIA")
    val tarjetaTurno get() = round2(280.0 + sum("TARJETA"))
    val cobrosTarjeta get() = 1 + n("TARJETA")
    val cobrosTurno get() = cobrosEfectivo + cobrosTransfer + cobrosTarjeta
}
