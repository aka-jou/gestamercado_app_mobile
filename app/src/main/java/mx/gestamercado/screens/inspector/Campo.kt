@file:OptIn(ExperimentalTextApi::class)

package mx.gestamercado.screens.inspector

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.ui.*
import mx.gestamercado.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Vista 1.4.A · Formulario de asistencia / acta de ausencia. */
@Composable
fun AsistenciaScreen(localId: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val l = app.local(localId)
    var tipo by remember { mutableIntStateOf(1) }
    var obs by remember { mutableStateOf("Cortina metálica abajo con candado. Sin mercancía visible.") }
    var foto by remember { mutableStateOf<String?>(null) }
    var sha by remember { mutableStateOf("") }
    var capturing by remember { mutableStateOf(false) }
    var dialogo by remember { mutableIntStateOf(0) }
    val tipos = listOf("Asistencia operativa", "Local cerrado / ausencia", "Negativa de pago")
    val actas = app.actas[l.id] ?: 0
    val siguiente = if (tipo == 0) actas else actas + 1

    fun guardar() {
        if (tipo != 0 && foto == null) { toast("La foto del local es obligatoria para un acta de ausencia o negativa."); return }
        if (tipo == 0) {
            app.registrarAsistencia(l.id)
            nav.pop()
            toast("Asistencia de ${l.id} registrada para cobro POR_VISITA.")
            return
        }
        val n = app.registrarActa(l.id)
        if (n >= 3) dialogo = n else {
            nav.pop()
            toast("Acta guardada con evidencia FOTO_LOCAL · ${l.id} lleva $n de 3.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        InspectorBar("${l.id} · ${fDia(app.hoy).uppercase()} · INSP. 3f9a…c21", "Registro de asistencia", back = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(l.nombre, style = sans(18, FontWeight.ExtraBold, ls = -0.3))
            VSpace(3)
            Text("Titular: ${l.titular}", style = sans(12.5, color = C.Muted2))
            VSpace(16)
            Text("TIPO DE REGISTRO", style = label())
            VSpace(6)
            BoxCard {
                tipos.forEachIndexed { i, t ->
                    val sel = i == tipo
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).background(if (sel) (if (i == 0) C.TealTint else C.Pink) else Color.White)
                            .clickable { tipo = i }.then(if (i < tipos.lastIndex) Modifier.borderBottom(1.dp, C.Line) else Modifier).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Diamond(13.dp, if (sel) (if (i == 0) C.TealDark else C.Magenta) else C.Ink, hollow = !sel)
                        HSpace(12)
                        Text(t, style = sans(14, if (sel) FontWeight.Bold else FontWeight.Normal))
                    }
                }
            }
            VSpace(14)
            BoxField("OBSERVACIONES", obs, { obs = it }, minLines = 3)
            VSpace(14)
            Row {
                Text(if (tipo == 0) "FOTO_LOCAL · OPCIONAL" else "FOTO_LOCAL · OBLIGATORIA", style = label(), modifier = Modifier.weight(1f))
                Text(if (foto == null) "0 DE 1" else "1 DE 1", style = label(if (tipo == 0) C.Muted else C.Magenta))
            }
            VSpace(6)
            BoxCard(onClick = if (capturing) null else ({
                capturing = true
                scope.launch {
                    delay(800)
                    val r = Random(System.currentTimeMillis())
                    foto = "IMG_20261014_${1100 + r.nextInt(80)}.jpg"
                    sha = hexOf(r, 64)
                    capturing = false
                }
            })) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.size(112.dp).background(if (foto == null) C.Sand else C.GrayTint), contentAlignment = Alignment.Center) {
                        if (capturing) CircularProgressIndicator(color = C.Magenta, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                        else Text(if (foto == null) "FOTO" else "JPG", style = label(C.Ink, 11), modifier = Modifier.background(Color.White).padding(6.dp, 3.dp))
                    }
                    Box(Modifier.width(1.5.dp).fillMaxHeight().background(C.Ink))
                    Column(Modifier.weight(1f).padding(12.dp)) {
                        if (foto == null) {
                            Text("Tomar foto del local", style = sans(14, FontWeight.Bold))
                            VSpace(4)
                            Text("Se guardan sha256 y coordenadas GPS en metadatos EXIF.", style = sans(12, color = C.Muted2, lh = 1.4))
                        } else {
                            Text(foto!!, style = mono(11.5, FontWeight.Medium))
                            VSpace(4)
                            Text("image/jpeg · 1.8 MB", style = mono(11, color = C.Body))
                            Text("sha256 ${corto(sha)}", style = mono(11, color = C.Body))
                            Text("EXIF GPS 19.04152, −98.20611", style = mono(11, color = C.TealDark))
                            VSpace(4)
                            Text("Toca para repetir", style = sans(11, color = C.Muted2))
                        }
                    }
                }
            }
            VSpace(14)
            Column(Modifier.fillMaxWidth().background(C.Ink).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ACTAS CONSECUTIVAS", style = label(C.Muted), modifier = Modifier.weight(1f))
                    Text("$actas / 3", style = mono(20, color = Color.White))
                }
                VSpace(10)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i -> Diamond(16.dp, C.Magenta, hollow = i >= actas, stroke = 2.dp) }
                }
                VSpace(10)
                Text(
                    when {
                        tipo == 0 -> "La asistencia no suma actas; sirve de base para el cobro POR_VISITA."
                        siguiente >= 3 -> "Esta acta será la $siguiente.ª: con más de 10 días cerrado el local se turna a recuperación por ABANDONO (Art. 20 Fracc. XXVIII)."
                        else -> "Esta será el acta $siguiente de 3 requeridas para el proceso de recuperación."
                    },
                    style = sans(12, color = C.Gray, lh = 1.5),
                )
            }
        }
        Box(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
            PrimaryButton(if (tipo == 0) "Guardar asistencia" else "Guardar acta de ausencia", { guardar() }, color = if (tipo == 0) C.Ink else C.Magenta)
        }
    }

    if (dialogo > 0) {
        AlertDialog(
            onDismissRequest = {},
            shape = RectangleShape,
            containerColor = Color.White,
            title = { Text("3 actas consecutivas", style = sans(18, FontWeight.ExtraBold)) },
            text = { Text("${l.id} acumula $dialogo actas de ausencia. Se turna al proceso administrativo de recuperación por ABANDONO (Art. 20 Fracc. XXVIII).", style = sans(14, color = C.Body, lh = 1.5)) },
            confirmButton = {
                TextButton(onClick = {
                    dialogo = 0
                    nav.pop()
                    toast("Acta guardada · ${l.id} turnado a recuperación.")
                }) { Text("Entendido", style = sans(14, FontWeight.Bold, C.Magenta)) }
            },
        )
    }
}

private data class Vecino(val id: String, val r: Rect)

/** Vista 1.5.A · Trazado geoespacial y datos del espacio (CU-06, CU-07). */
@Composable
fun AltaLocalScreen() {
    val nav = LocalNav.current
    val toast = LocalToast.current
    val tm = rememberTextMeasurer()
    val k = 0.03f
    val tolerancia = 0.25
    val vecinos = remember {
        listOf(Vecino("B-02", Rect(16f, 34f, 86f, 130f)), Vecino("B-03", Rect(92f, 34f, 162f, 130f)), Vecino("B-05", Rect(260f, 34f, 330f, 130f)))
    }
    val tipos = listOf("FIJO", "SEMIFIJO", "AMBULANTE", "PORTAL", "VIA_PUBLICA")
    val zonas = listOf("Pasillo B", "Portal Hidalgo", "Vía pública N")
    val pts = remember { mutableStateListOf(Offset(150f, 52f), Offset(248f, 52f), Offset(248f, 164f), Offset(150f, 164f)) }
    var num by remember { mutableStateOf("B-04N") }
    var folio by remember { mutableStateOf("T-0412") }
    var nombre by remember { mutableStateOf("Semillas y Chiles Rosy") }
    var giro by remember { mutableStateOf("Semillas") }
    var areaOf by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var tipo by remember { mutableIntStateOf(0) }
    var zona by remember { mutableIntStateOf(0) }

    val areaPx = if (pts.size < 3) 0f else {
        var s = 0f
        for (i in pts.indices) {
            val a = pts[i]; val b = pts[(i + 1) % pts.size]
            s += a.x * b.y - b.x * a.y
        }
        abs(s) / 2
    }
    val area = round2((areaPx * k * k).toDouble())
    val perimetro = if (pts.size < 2) 0.0 else {
        var s = 0f
        val n = if (pts.size < 3) pts.size - 1 else pts.size
        for (i in 0 until n) s += (pts[(i + 1) % pts.size] - pts[i]).getDistance()
        round2((s * k).toDouble())
    }
    val centro = if (pts.isEmpty()) Offset.Zero else Offset(pts.map { it.x }.average().toFloat(), pts.map { it.y }.average().toFloat())
    val bbox = if (pts.size < 3) null else Rect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    var tr: Triple<Vecino, Rect, Double>? = null
    if (bbox != null) for (v in vecinos) {
        val i = bbox.intersect(v.r)
        if (i.width <= 0 || i.height <= 0) continue
        val a = round2((i.width * i.height * k * k).toDouble())
        if (tr == null || a > tr.third) tr = Triple(v, i, a)
    }
    val overlap = tr != null && tr.third > tolerancia
    val tipoS = tipos[tipo]
    val primerCuadro = zona == 1 && tipoS == "FIJO"
    val cercaEscuela = zona == 2
    val areaOfNum = areaOf.trim().toDoubleOrNull()
    val areaEf = areaOfNum ?: area
    val listo = pts.size >= 3 && nombre.isNotBlank() && num.isNotBlank()
    val bloqueo = overlap || primerCuadro || cercaEscuela

    val checks = buildList {
        if (pts.size < 3) add(Triple(Tone.INFO, "Captura al menos 3 vértices", "Toca el mapa para registrar cada esquina con el GPS."))
        val t = tr
        if (t != null && overlap) add(Triple(Tone.DANGER, "Traslape con ${t.first.id} · ${f2(t.third)} m²", "Supera la tolerancia de 0.25 m² (trg_local_geometria). Ajusta los vértices."))
        if (t != null && !overlap) add(Triple(Tone.OK, "Traslape con ${t.first.id} dentro de tolerancia", "${f2(t.third)} m² ≤ 0.25 m²"))
        if (t == null && pts.size >= 3) add(Triple(Tone.OK, "Sin traslapes", "No intersecta otros puestos."))
        add(
            if (primerCuadro) Triple(Tone.DANGER, "Intersecta PRIMER_CUADRO", "RN-PAD-04 · tipo FIJO prohibido en esta zona. Bloqueo total.")
            else Triple(Tone.OK, "Fuera de primer cuadro", "RN-PAD-04 · " + if (tipoS == "FIJO") "tipo FIJO permitido aquí." else "no aplica a $tipoS."),
        )
        add(
            if (cercaEscuela) Triple(Tone.DANGER, "A 86 m de Escuela Primaria Benito Juárez", "RN-PAD-03 · menor a 100 m. Bloqueo total.")
            else Triple(Tone.OK, "A 142 m de Escuela Primaria Benito Juárez", "RN-PAD-03 · cumple la distancia mínima de 100 m."),
        )
    }

    Column(Modifier.fillMaxSize()) {
        InspectorBar("ADMIN. DE MERCADO · ${zonas[zona].uppercase()}", "Alta de local en campo", back = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BoxWithConstraints(Modifier.fillMaxWidth().background(C.Sand).borderBottom()) {
                val sc = constraints.maxWidth / 362f
                val u = maxWidth.value / 362f
                val hDp = maxWidth * (270f / 362f)
                val conflicto = if (overlap) tr?.first?.r else null
                val inter = tr?.second
                Box(Modifier.fillMaxWidth().height(hDp)) {
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures { o -> pts.add(Offset(o.x / sc, o.y / sc)) }
                        },
                    ) {
                        val st = TextStyleHelper
                        drawText(tm, "PASILLO B", Offset(16f * sc, 12f * sc), st.label(u))
                        vecinos.forEach { v ->
                            val isC = conflicto == v.r
                            val r = Rect(v.r.left * sc, v.r.top * sc, v.r.right * sc, v.r.bottom * sc)
                            drawRect(if (isC) C.Pink else C.GrayTint, r.topLeft, r.size)
                            drawRect(if (isC) C.Magenta else C.Ink, r.topLeft, r.size, style = Stroke(if (isC) 2.5.dp.toPx() else 1.5.dp.toPx()))
                            drawText(tm, v.id, Offset(r.left + 5f * sc, r.bottom - 17f * sc), st.id(u, isC))
                        }
                        if (pts.size >= 2) {
                            val path = Path().apply {
                                moveTo(pts[0].x * sc, pts[0].y * sc)
                                for (p in pts.drop(1)) lineTo(p.x * sc, p.y * sc)
                                if (pts.size >= 3) close()
                            }
                            if (pts.size >= 3) drawPath(path, Color(0x291C9988))
                            drawPath(path, C.TealDark, style = Stroke(2.dp.toPx()))
                        }
                        if (inter != null && inter.width > 0 && inter.height > 0) {
                            drawRect(Color(0x8CC80766), Offset(inter.left * sc, inter.top * sc), Size(inter.width * sc, inter.height * sc))
                        }
                        pts.forEachIndexed { i, p ->
                            val c = Offset(p.x * sc, p.y * sc)
                            rotate(45f, c) {
                                val s = 10.dp.toPx()
                                drawRect(Color.White, Offset(c.x - s / 2 - 2.dp.toPx(), c.y - s / 2 - 2.dp.toPx()), Size(s + 4.dp.toPx(), s + 4.dp.toPx()))
                                drawRect(if (i == pts.lastIndex) C.Magenta else C.Ink, Offset(c.x - s / 2, c.y - s / 2), Size(s, s))
                            }
                        }
                    }
                    Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.weight(1f).height(44.dp).background(C.Ink).clickable {
                                pts.add(if (pts.isEmpty()) Offset(180f, 120f) else pts.last() + Offset(14f, 10f))
                            },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Diamond(9.dp, C.Magenta)
                            HSpace(8)
                            Text("Capturar vértice · ${pts.size}", style = sans(13, FontWeight.Bold, Color.White))
                        }
                        GhostButton("Deshacer", if (pts.isEmpty()) null else ({ pts.removeAt(pts.lastIndex) }), Modifier.width(92.dp), height = 44.dp, fill = Color.White)
                        GhostButton("Limpiar", { pts.clear() }, Modifier.width(82.dp), height = 44.dp, fill = Color.White)
                    }
                }
            }
            Box(Modifier.background(Color.White).borderBottom()) {
                MetricRow(border = false) {
                    MetricCell("ÁREA", "${f2(area)} m²", Modifier.weight(1f).fillMaxHeight(), vs = 15)
                    VLine()
                    MetricCell("PERÍMETRO", "${f2(perimetro)} m", Modifier.weight(1f).fillMaxHeight(), vs = 15)
                    VLine()
                    MetricCell(
                        "CENTROIDE",
                        "${String.format(java.util.Locale.US, "%.5f", 19.04150 - centro.y * 0.0000003)}\n${String.format(java.util.Locale.US, "%.5f", -98.20650 + centro.x * 0.0000003)}",
                        Modifier.weight(1f).fillMaxHeight(), vs = 11,
                    )
                }
            }
            Column(Modifier.padding(16.dp)) {
                checks.forEach { (tone, t, d) ->
                    Notice(d, t, tone)
                    VSpace(8)
                }
                VSpace(8)
                SectionTitle("DATOS DEL ESPACIO")
                VSpace(12)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoxField("NÚMERO DE LOCAL", num, { num = it }, Modifier.weight(1f), monoFont = true)
                    BoxField("FOLIO TARJETÓN", folio, { folio = it }, Modifier.weight(1f), monoFont = true)
                }
                VSpace(12)
                BoxField("NOMBRE COMERCIAL", nombre, { nombre = it })
                VSpace(12)
                Text("ZONA · zona_id", style = label())
                VSpace(6)
                Segmented(zonas, zona) { zona = it }
                VSpace(12)
                Text("TIPO DE ESPACIO", style = label())
                VSpace(6)
                FlowRowCompat(tipos.mapIndexed { i, t -> t to (i == tipo) }) { i -> tipo = i }
                VSpace(12)
                BoxField("GIRO OBSERVADO", giro, { giro = it })
                VSpace(12)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoxField("ÁREA OFICIAL m² · OPCIONAL", areaOf, { areaOf = it }, Modifier.weight(1f), monoFont = true, hint = f2(area), keyboardType = KeyboardType.Decimal)
                    BoxField("MOTIVO", motivo, { motivo = it }, Modifier.weight(1f), hint = "Contrato")
                }
                VSpace(6)
                Text(if (areaOfNum == null) "Sin metraje contractual: se cobra sobre el área calculada." else "Se cobrará sobre el área oficial (area_oficial_m2).", style = sans(11.5, color = C.Muted2))
                VSpace(14)
                Row(Modifier.fillMaxWidth().background(C.Ink).padding(15.dp, 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SIMULACIÓN · CU-16", style = label(C.Muted, 10))
                        VSpace(3)
                        Text("${f2(cuotaBaseUma(tipoS))} + ${f2(max(0.0, areaEf - metrosLibres(tipoS)))} × 0.50 UMA/mes", style = sans(11.5, color = C.Gray))
                    }
                    Text("${money(cuotaDiaria(tipoS, areaEf))}/día", style = mono(18, color = Color.White))
                }
                VSpace(14)
                PrimaryButton(
                    if (bloqueo) "Guardar · bloqueado" else "Guardar local",
                    if (!listo || bloqueo) null else ({
                        nav.pop()
                        toast("Local ${num.trim()} guardado · geom Polygon 4326 · ${f2(areaEf)} m²")
                    }),
                    color = C.Ink,
                )
            }
        }
    }
}

private object TextStyleHelper {
    fun label(u: Float) = androidx.compose.ui.text.TextStyle(fontFamily = Archivo, fontWeight = FontWeight.Bold, color = C.Muted2, fontSize = (9.5f * u).sp, letterSpacing = 1.3.sp)
    fun id(u: Float, conflicto: Boolean) = androidx.compose.ui.text.TextStyle(fontFamily = DmMono, fontWeight = FontWeight.Medium, color = if (conflicto) C.Magenta else C.Ink, fontSize = (10f * u).sp)
}

/** Fila de chips con salto de línea simple (sin APIs experimentales). */
@Composable
private fun FlowRowCompat(items: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.withIndex().chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (i, item) -> SelectChip(item.first, item.second) { onSelect(i) } }
            }
        }
    }
}
