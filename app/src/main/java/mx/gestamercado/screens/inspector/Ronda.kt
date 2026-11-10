package mx.gestamercado.screens.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalNav
import mx.gestamercado.Route
import mx.gestamercado.data.LocalStatus
import mx.gestamercado.ui.*
import mx.gestamercado.util.f2
import mx.gestamercado.util.money

@Composable
fun InspectorShell() {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (app.inspTab) {
                0 -> MapaScreen()
                1 -> SyncScreen()
                else -> CorteScreen()
            }
        }
        val p = app.pendientes
        BrandNav(listOf("Ronda", if (p > 0) "Sincronización · $p" else "Sincronización", "Corte"), app.inspTab) { app.inspTab = it }
    }
}

/** Vista 1.2.A · Mapa interactivo de ronda y buscador. */
@Composable
fun MapaScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    var filtro by remember { mutableStateOf<LocalStatus?>(null) }
    var q by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    LaunchedEffect(scanning) {
        if (scanning) {
            delay(1000)
            scanning = false
            sheet = "A-08"
        }
    }

    val qq = q.trim().lowercase()
    val results = if (qq.isEmpty()) emptyList() else app.locales.filter { "${it.id} ${it.nombre} ${it.tarjeton} ${it.titular}".lowercase().contains(qq) }.take(5)
    fun count(s: LocalStatus) = app.locales.count { app.statusDe(it) == s }
    val chips = listOf(
        Triple("Todos ${app.locales.size}", null, null),
        Triple("Pendiente ${count(LocalStatus.PENDIENTE)}", LocalStatus.PENDIENTE, C.Yellow),
        Triple("Moroso ${count(LocalStatus.MOROSO)}", LocalStatus.MOROSO, C.Magenta),
        Triple("Al corriente ${count(LocalStatus.AL_CORRIENTE)}", LocalStatus.AL_CORRIENTE, C.Teal),
        Triple("Desocupado ${count(LocalStatus.DESOCUPADO)}", LocalStatus.DESOCUPADO, C.Gray),
    )

    Column(Modifier.fillMaxSize()) {
        InspectorBar("MERCADO PONIENTE · MP-01", "Ronda de cobro", actions = { BarAction("+ Alta") { nav.push(Route.AltaLocal) } })
        Column(Modifier.fillMaxWidth().borderBottom().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = sans(14.5),
                placeholder = { Text("Local, tarjetón o titular", style = sans(14, color = C.Muted)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = C.Ink) },
                trailingIcon = if (q.isNotEmpty()) { { IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpiar", tint = C.Ink) } } } else null,
                shape = RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.Magenta, unfocusedBorderColor = C.Ink,
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, cursorColor = C.Magenta,
                ),
            )
            VSpace(10)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (t, s, dot) -> SelectChip(t, filtro == s, dot) { filtro = s } }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(C.Sand)) {
            val k = maxWidth.value / 386f
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height((470 * k).dp)) {
                    Box(Modifier.offset((12 * k).dp, (16 * k).dp).size((362 * k).dp, (400 * k).dp).background(C.Bg).border(2.dp, C.Ink))
                    listOf("PASILLO A · TORTILLAS Y MASA" to 28f, "PASILLO B · ABARROTES" to 154f, "PASILLO C · FRUTAS Y LEGUMBRES" to 280f).forEach { (t, y) ->
                        Text(t, style = label(C.Muted2, 9.5 * k), modifier = Modifier.offset((24 * k).dp, (y * k).dp))
                    }
                    app.locales.forEach { l ->
                        val s = app.statusDe(l)
                        val match = filtro == null || filtro == s
                        Box(
                            Modifier.offset((l.x * k).dp, (l.y * k).dp).size((50 * k).dp, (70 * k).dp).alpha(if (match) 1f else 0.22f)
                                .background(statusBg(s)).border(1.5.dp, C.Ink).clickable { sheet = l.id }.padding((5 * k).dp),
                            contentAlignment = Alignment.BottomStart,
                        ) {
                            Text(l.id, style = mono(10 * k, FontWeight.Medium, statusFg(s)))
                        }
                    }
                    Row(Modifier.offset((166 * k).dp, (140 * k).dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(18.dp).background(Color.White, CircleShape).padding(4.dp).background(C.Ink, CircleShape))
                        HSpace(5)
                        Text("TÚ", style = sans(10, FontWeight.Bold, Color.White), modifier = Modifier.background(C.Ink).padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 14.dp, end = 86.dp)) {
                BoxCard(padding = PaddingValues(10.dp, 9.dp)) {
                    val all = LocalStatus.entries
                    for (row in all.chunked(2)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                            row.forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(Modifier.size(11.dp).background(statusBg(s)).border(1.dp, C.Ink))
                                    HSpace(6)
                                    Text(statusName(s), style = sans(11))
                                }
                            }
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 14.dp).size(60.dp).background(C.Magenta).border(1.5.dp, C.Ink).clickable { scanning = true },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                QrGlyph()
                VSpace(4)
                Text("QR", style = sans(9.5, FontWeight.Bold, Color.White, 0.8))
            }
            if (results.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    BoxCard {
                        results.forEach { l ->
                            Row(
                                Modifier.fillMaxWidth().clickable { sheet = l.id }.borderBottom(1.dp, C.Line).padding(12.dp, 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).background(statusBg(app.statusDe(l))).border(1.dp, C.Ink))
                                HSpace(10)
                                Text(l.id, style = mono(12.5, FontWeight.Medium), modifier = Modifier.width(44.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(l.nombre, style = sans(13.5, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${l.tarjeton} · ${l.titular}", style = sans(11.5, color = C.Muted2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sheet?.let { id -> LocalSheet(id) { sheet = null } }

    if (scanning) {
        Dialog(onDismissRequest = {}) {
            Column(Modifier.background(Color.White).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(180.dp).background(C.Sand).border(1.5.dp, C.Ink), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = C.Magenta, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                }
                VSpace(16)
                Text("Leyendo tarjetón QR…", style = sans(15, FontWeight.Bold))
                VSpace(4)
                Text("Cámara simulada", style = sans(12, color = C.Muted2))
            }
        }
    }
}

/** Vista 1.2.B · Tarjeta resumen del local (bottom sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalSheet(localId: String, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val l = app.local(localId)
    val s = app.statusDe(l)
    val adeudo = app.adeudo(l.id)
    val des = l.base == LocalStatus.DESOCUPADO
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RectangleShape,
        containerColor = C.Bg,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(44.dp, 4.dp).background(C.Gray)) },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 14.dp)) {
                Box(Modifier.defaultMinSize(minWidth = 64.dp).height(64.dp).background(statusBg(s)).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(l.id, style = mono(17, FontWeight.Medium, statusFg(s)))
                }
                HSpace(14)
                Column(Modifier.weight(1f)) {
                    Text(l.nombre, style = sans(19, FontWeight.ExtraBold, ls = -0.4, lh = 1.1))
                    VSpace(5)
                    Text("Tarjetón ${l.tarjeton} · ${l.zona.substringBefore(" · ")}", style = mono(12, color = C.Muted2))
                    VSpace(7)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tag(l.tipo)
                        Tag(l.estatus, if (des) C.Gray else C.TealDark, if (des) C.Ink else Color.White)
                        Tag(statusName(s).uppercase(), statusBg(s), statusFg(s))
                    }
                }
            }
            Box(Modifier.borderTop(1.dp, C.Line).borderBottom()) {
                MetricRow(border = false) {
                    MetricCell("ADEUDO", money(adeudo), Modifier.weight(1f).fillMaxHeight(), bg = if (adeudo > 0) C.Pink else Color.White, tc = if (adeudo > 0) C.Magenta else C.Muted, vc = if (adeudo > 0) C.Magenta else C.Ink)
                    VLine()
                    MetricCell("VENCIDOS", "${app.vencidos(l.id)} cargos", Modifier.weight(1f).fillMaxHeight())
                    VLine()
                    MetricCell("A FAVOR", money(0.0), Modifier.weight(1f).fillMaxHeight())
                }
            }
            Column(Modifier.padding(18.dp, 14.dp)) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("ÁREA EFECTIVA", style = label(size = 10))
                        VSpace(4)
                        Text("${f2(l.area)} m²", style = mono(14.5))
                        VSpace(2)
                        Text(if (l.areaCalculada != null) "Oficial · calculada ${f2(l.areaCalculada)} m²" else "area_calculada_m2 · PostGIS", style = sans(11.5, color = C.Muted2))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("CONCESIÓN", style = label(size = 10))
                        VSpace(4)
                        Text(l.concesion, style = sans(14.5, FontWeight.Bold, if (des) C.Muted2 else C.TealDark))
                        VSpace(2)
                        Text(if (des) "Sin titular" else if (l.formal) "Formal" else "Informal · es_formal = false", style = sans(11.5, color = if (l.formal) C.Muted2 else C.Magenta))
                    }
                }
                if (!des) {
                    VSpace(14)
                    Text("TITULAR · v_persona_publica", style = label(size = 10))
                    VSpace(4)
                    Text(l.titular, style = sans(15, FontWeight.Bold))
                    VSpace(2)
                    Text("CURP y RFC ocultos por RLS para el rol inspector.", style = sans(11.5, color = C.Muted2))
                    VSpace(14)
                    Text("SUPLENTES AUTORIZADOS · ${l.suplentes.size} DE 2", style = label(size = 10))
                    VSpace(6)
                    if (l.suplentes.isEmpty()) Text("Sin suplentes registrados", style = sans(13, color = C.Muted2))
                    l.suplentes.forEach { su ->
                        Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Diamond(8.dp, C.Teal)
                            HSpace(8)
                            Text(su.nombre, style = sans(13.5), modifier = Modifier.weight(1f))
                            Text(su.parentesco, style = label(C.Body, 10))
                        }
                    }
                    if (l.giroObservado != l.giroAutorizado) {
                        VSpace(8)
                        Notice("Autorizado: ${l.giroAutorizado}. Observado en ronda: ${l.giroObservado}.", "GIRO_NO_AUTORIZADO", Tone.WARN)
                    }
                }
            }
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                PrimaryButton(
                    if (des) "Local sin concesión" else "Realizar cobro",
                    if (des) null else ({ onDismiss(); nav.push(Route.Cobro(l.id)) }),
                    arrow = !des,
                )
                VSpace(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Registrar incidencia", { onDismiss(); nav.push(Route.Asistencia(l.id)) }, Modifier.weight(1f))
                    GhostButton("Historial de pagos", { onDismiss(); nav.push(Route.Historial(l.id)) }, Modifier.weight(1f))
                }
            }
        }
    }
}
