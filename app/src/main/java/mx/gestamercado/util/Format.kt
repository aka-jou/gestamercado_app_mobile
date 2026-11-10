package mx.gestamercado.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

const val UMA = 117.31
const val EXCEDENTE_UMA = 0.5

fun round2(v: Double): Double = Math.round(v * 100 + if (v >= 0) 1e-6 else -1e-6) / 100.0

fun money(v: Double): String = (if (v < 0) "−" else "") + "\$" + String.format(Locale.US, "%,.2f", abs(round2(v)))

fun f2(v: Double): String = String.format(Locale.US, "%.2f", v)

/** CUOTA_BASE_MAS_EXCEDENTE (RN-FIS-03): FIJO 3.00 UMA con 3 m² libres; SEMIFIJO y otros 1.50 UMA con 1 m² libre. */
fun cuotaBaseUma(tipo: String) = if (tipo == "FIJO") 3.0 else 1.5
fun metrosLibres(tipo: String) = if (tipo == "FIJO") 3.0 else 1.0
fun umaMes(tipo: String, area: Double) = round2(cuotaBaseUma(tipo) + max(0.0, area - metrosLibres(tipo)) * EXCEDENTE_UMA)
fun cuotaMensual(tipo: String, area: Double) = round2(umaMes(tipo, area) * UMA)

/** RN-FIS-01: cuota diaria = cuota mensual UMA × valor UMA × 12 / 365 */
fun cuotaDiaria(tipo: String, area: Double) = round2(umaMes(tipo, area) * UMA * 12 / 365)

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
val MESES_LARGOS = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
private val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

fun two(n: Int) = n.toString().padStart(2, '0')
fun fDia(d: LocalDate) = "${two(d.dayOfMonth)} ${MESES[d.monthValue - 1]} ${d.year}"
fun fDia(d: LocalDateTime) = fDia(d.toLocalDate())
fun fDiaLargo(d: LocalDate) = "${DIAS[d.dayOfWeek.value - 1]} ${fDia(d)}"
fun fFecha(d: LocalDateTime) = "${fDia(d)} · ${two(d.hour)}:${two(d.minute)}"
fun fCorta(d: LocalDateTime) = "${two(d.dayOfMonth)} ${MESES[d.monthValue - 1]} ${two(d.hour)}:${two(d.minute)}"
fun fHora(d: LocalDateTime) = "${two(d.hour)}:${two(d.minute)}:${two(d.second)}"
fun fTicket(d: LocalDateTime) = "${two(d.dayOfMonth)}/${two(d.monthValue)}/${two(d.year % 100)} ${fHora(d)}"

fun hexOf(r: Random, n: Int): String {
    val h = "0123456789abcdef"
    return (1..n).map { h[r.nextInt(16)] }.joinToString("")
}

fun uuidOf(r: Random) = "${hexOf(r, 8)}-${hexOf(r, 4)}-4${hexOf(r, 3)}-a${hexOf(r, 3)}-${hexOf(r, 12)}"

fun corto(s: String, a: Int = 4, b: Int = 4) = if (s.length <= a + b) s else "${s.take(a)}…${s.takeLast(b)}"
