package mx.gestamercado.screens.locatario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Route
import mx.gestamercado.data.Cargo
import mx.gestamercado.ui.*
import mx.gestamercado.util.*
import kotlin.math.max

const val LOCAL_LOCATARIO = "A-08"

@Composable
fun LocatarioShell() {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (app.locTab) {
                0 -> MiLocalScreen { app.locTab = 1 }
                1 -> EstadoCuentaScreen()
                2 -> SimuladorScreen()
                else -> RecibosScreen()
            }
        }
        BrandNav(listOf("Mi local", "Cuenta", "Simulador", "Recibos"), app.locTab) { app.locTab = it }
    }
}

/** Vista 2.2.A · Dashboard principal / tarjetón digital QR. */
@Composable
fun MiLocalScreen(onGoCuenta: () -> Unit) {
    val app = LocalApp.current
    val toast = LocalToast.current
    val l = app.local(LOCAL_LOCATARIO)
    val adeudo = app.adeudo(l.id)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().background(C.Ink).clipToBounds().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 22.dp)) {
            DecoDiamond(200.dp, C.Teal, Alignment.CenterEnd, 70.dp, 40.dp)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mark(30.dp)
                    HSpace(12)
                    Column(Modifier.weight(1f)) {
                        Text("HOLA, MARÍA GUADALUPE", style = label(C.Muted, 10))
                        VSpace(2)
                        Text("Mi local", style = sans(17, FontWeight.Bold, Color.White))
                    }
                    Avatar("MG", C.Magenta)
                }
                VSpace(16)
                BoxCard(padding = PaddingValues(18.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        QrCode("GM-TARJETON|${l.id}|${l.tarjeton}|c4b1e9a2-71d0-4f3a-a8e5-0187a08mp01", 190.dp)
                        VSpace(12)
                        Text("${l.tarjeton} · ${l.id}", style = mono(22, FontWeight.Medium))
                        VSpace(4)
                        Text("Muestra este código al inspector en la ronda", style = sans(12, color = C.Muted2))
                    }
                }
            }
        }
        if (adeudo > 0) {
            Row(Modifier.fillMaxWidth().background(C.Pink).clickable(onClick = onGoCuenta).padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Diamond(9.dp, C.Magenta)
                HSpace(10)
                Text("Tienes ${money(adeudo)} pendientes · ${app.vencidos(l.id)} cargos vencidos", style = sans(13, FontWeight.SemiBold, C.Magenta), modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = C.Magenta, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp)) {
            SectionTitle("FICHA TÉCNICA")
            VSpace(10)
            BoxCard {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(132.dp).fillMaxHeight().defaultMinSize(minHeight = 160.dp).background(C.Sand)) {
                        listOf(Triple(8, 20, false), Triple(46, 20, true), Triple(88, 20, false), Triple(8, 96, false), Triple(46, 96, false), Triple(88, 96, false)).forEach { (x, y, me) ->
                            Box(Modifier.offset(x.dp, y.dp).size(if (me) 38.dp else 34.dp, 52.dp).background(if (me) C.Magenta else C.GrayTint).border(if (me) 1.5.dp else 1.dp, C.Ink))
                        }
                        Text("PASILLO A", style = label(C.Muted2, 8.5), modifier = Modifier.offset(8.dp, 78.dp))
                    }
                    Box(Modifier.width(1.5.dp).fillMaxHeight().background(C.Ink))
                    Column(Modifier.weight(1f).padding(14.dp, 12.dp)) {
                        Text(l.nombre, style = sans(16, FontWeight.ExtraBold, lh = 1.15))
                        VSpace(6)
                        Text("Mercado Poniente · ${l.zona.substringBefore(" · ")} · Local ${l.id}", style = sans(12, color = C.Body, lh = 1.4))
                        VSpace(8)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Tag(l.tipo)
                            Tag(l.estatus, C.TealDark)
                        }
                        VSpace(9)
                        Text("SUPERFICIE DE COBRO", style = label(size = 9.5))
                        VSpace(2)
                        Text("${f2(l.area)} m²", style = mono(15))
                    }
                }
            }
            VSpace(18)
            SectionTitle("CONCESIÓN") { Tag(l.concesion, C.TealDark) }
            VSpace(10)
            BoxCard(padding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                KV("Titular", l.titular)
                KV("CURP", "HEOG780412MPLRRD05", monoV = true)
                KV("RFC", "HEOG780412KJ3", monoV = true)
                KV("Giro autorizado", l.giroAutorizado)
                KV("Expediente", "DM/CON/2019/0187", monoV = true)
                KV("Vigencia", "01 mar 2019 – 28 feb 2029", monoV = true, last = true)
            }
            VSpace(18)
            SectionTitle("SUPLENTES · RN-PAD-02") { Text("${l.suplentes.size} / 2", style = mono(11, color = C.Muted2)) }
            VSpace(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                l.suplentes.forEach { s ->
                    BoxCard(Modifier.weight(1f), padding = PaddingValues(12.dp)) {
                        Box(Modifier.size(32.dp).background(C.Teal), contentAlignment = Alignment.Center) { Text(s.iniciales, style = sans(11, FontWeight.Bold, Color.White)) }
                        VSpace(8)
                        Text(s.nombre, style = sans(13, FontWeight.Bold, lh = 1.25))
                        VSpace(6)
                        Text(s.parentesco, style = label(C.Body, 10))
                    }
                }
            }
            VSpace(18)
            SectionTitle("EXPEDIENTE DIGITAL")
            VSpace(10)
            BoxCard {
                listOf(
                    Triple("CONTRATO", "contrato_concesion_A08.pdf", "PDF · 412 KB · 01 mar 2019"),
                    Triple("DICTAMEN", "dictamen_PC_2025.pdf", "PDF · 1.2 MB · 14 ene 2025"),
                    Triple("IDENTIFICACIÓN", "ine_titular.pdf", "PDF · 680 KB · 01 mar 2019"),
                ).forEach { (t, n, s) ->
                    Row(Modifier.fillMaxWidth().clickable { toast("Descargando $n…") }.borderBottom(1.dp, C.Line).padding(14.dp, 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t, style = label(C.TealDark, 10), modifier = Modifier.width(92.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n, style = mono(12.5), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            VSpace(2)
                            Text(s, style = sans(11, color = C.Muted))
                        }
                        Box(Modifier.size(32.dp).border(1.5.dp, C.Ink), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descargar", tint = C.Ink, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Vista 2.3.A · Estado de cuenta y desglose de adeudos (CU-22). */
@Composable
fun EstadoCuentaScreen() {
    val app = LocalApp.current
    val l = app.local(LOCAL_LOCATARIO)
    var filtro by remember { mutableStateOf<String?>(null) }
    val cargos = app.cargosDe(l.id).reversed()
    val adeudo = app.adeudo(l.id)
    val vencidos = cargos.filter { it.saldo > 0 && !it.alVuelo }
    val desde = vencidos.lastOrNull()?.periodo?.substringBefore(" – ")
    val list = if (filtro == null) cargos else cargos.filter { it.estado == filtro }
    val estados = listOf("PENDIENTE", "PARCIAL", "PAGADO", "CANCELADO")

    Column(Modifier.fillMaxSize()) {
        SimpleBar("${l.id} · ${l.nombre.uppercase()}", "Estado de cuenta", avatar = "MG")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (adeudo > 0) HeroAmount("SALDO TOTAL ADEUDADO", money(adeudo), "${app.vencidos(l.id)} cargos vencidos" + if (desde != null) " · adeudo desde $desde 2026" else "")
            else HeroAmount("SALDO TOTAL ADEUDADO", money(0.0), "Estás al corriente. ¡Gracias!", C.TealDark)
            VSpace(12)
            BoxCard(padding = PaddingValues(15.dp, 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SALDO A FAVOR", style = label(C.TealDark, 10))
                        VSpace(3)
                        Text("Se aplica solo por FIFO al siguiente cargo (CU-17).", style = sans(11.5, color = C.Muted2, lh = 1.4))
                    }
                    Text(money(0.0), style = mono(20))
                }
            }
            VSpace(12)
            Column(Modifier.fillMaxWidth().background(C.YellowTint).border(1.5.dp, C.Ink).padding(15.dp, 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OMISIONES EN OCTUBRE · RN-FIS-02", style = label(C.YellowText, 10.5), modifier = Modifier.weight(1f))
                    Text("3 / 5", style = mono(17))
                }
                VSpace(9)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { i ->
                        Box(Modifier.weight(1f).height(8.dp).then(if (i < 3) Modifier.background(C.Yellow) else Modifier.background(Color.White).border(1.dp, C.Ink)))
                    }
                }
                VSpace(9)
                Text("Al 5.º incumplimiento se revoca el pago diario y se genera una PENALIZACIÓN por el mes completo vencido.", style = sans(12, color = C.Body, lh = 1.45))
            }
            VSpace(14)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SelectChip("TODOS ${cargos.size}", filtro == null) { filtro = null }
                    estados.take(2).forEach { e -> SelectChip("$e ${cargos.count { it.estado == e }}", filtro == e) { filtro = e } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    estados.drop(2).forEach { e -> SelectChip("$e ${cargos.count { it.estado == e }}", filtro == e) { filtro = e } }
                }
            }
            VSpace(12)
            if (list.isEmpty()) Text("Sin cargos con este estado.", textAlign = TextAlign.Center, style = sans(13.5, color = C.Muted2), modifier = Modifier.fillMaxWidth().padding(20.dp))
            list.forEach { c ->
                CargoCard(c)
                VSpace(8)
            }
        }
    }
}

@Composable
private fun CargoCard(c: Cargo) {
    val (cb, cf) = when (c.concepto) {
        "RECARGO" -> C.Pink to C.Magenta
        "PENALIZACION" -> C.YellowTint to C.YellowText
        "MULTA" -> C.Magenta to Color.White
        else -> C.Ink to Color.White
    }
    BoxCard {
        Column(Modifier.padding(13.dp, 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(if (c.concepto == "RECARGO") "RECARGO 1.25%" else c.concepto, cb, cf)
                Spacer(Modifier.weight(1f))
                TagEstado(c.estado)
            }
            VSpace(7)
            Text(c.periodo, style = sans(14, FontWeight.Bold))
            VSpace(5)
            Text(c.calculo, style = mono(11, color = C.Muted2, lh = 1.5))
            if (c.origen != null) {
                VSpace(3)
                Text("Origen: cargo #${c.origen}", style = mono(11, color = C.Magenta))
            }
        }
        Box(Modifier.borderTop(1.dp, C.Line)) {
            MetricRow(border = false) {
                MetricCell("TOTAL", money(c.total), Modifier.weight(1f).fillMaxHeight(), vs = 12.5)
                VLine()
                MetricCell("ABONADO", money(c.pagado), Modifier.weight(1f).fillMaxHeight(), vs = 12.5)
                VLine()
                MetricCell("SALDO", money(c.saldo), Modifier.weight(1f).fillMaxHeight(), vs = 12.5, vc = if (c.saldo > 0) C.Magenta else C.TealDark)
            }
        }
    }
}

/** Vista 2.4.A · Calculadora y desglose normativo (CU-16 / RN-FIS-01, RN-FIS-03). */
@Composable
fun SimuladorScreen() {
    var mes by remember { mutableIntStateOf(10) }
    var tipo by remember { mutableIntStateOf(0) }
    var areaTxt by remember { mutableStateOf("6.00") }
    var menu by remember { mutableStateOf(false) }
    val tipoS = if (tipo == 0) "FIJO" else "SEMIFIJO"
    val area = areaTxt.trim().toDoubleOrNull() ?: 0.0
    val base = cuotaBaseUma(tipoS)
    val libres = metrosLibres(tipoS)
    val exc = max(0.0, area - libres)
    val excUma = round2(exc * EXCEDENTE_UMA)
    val totalUma = umaMes(tipoS, area)
    val mensual = cuotaMensual(tipoS, area)
    val diaria = cuotaDiaria(tipoS, area)
    val pasos = listOf(
        "Cuota base $tipoS: incluye los primeros ${libres.toInt()} m² libres" to "${f2(base)} UMA/mes",
        "Excedente: ${f2(area)} − ${f2(libres)} = ${f2(exc)} m² × 0.50 UMA" to "+ ${f2(excUma)} UMA/mes",
        "Tarifa mensual en UMA" to "= ${f2(totalUma)} UMA/mes",
        "Por valor diario de la UMA del mercado (uma_aplicada)" to "${f2(totalUma)} × ${money(UMA)} = ${money(mensual)}/mes",
    )

    Column(Modifier.fillMaxSize()) {
        SimpleBar("fn_calcular_cuota · LEY DE INGRESOS", "¿Cuánto debo pagar?", avatar = "MG")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("PERIODO", style = label(size = 9.5))
                    VSpace(6)
                    Box {
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).background(Color.White).border(1.5.dp, C.Ink).clickable { menu = true }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${MESES_LARGOS[mes]} 2026", style = sans(14), modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = C.Ink)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            (9..11).forEach { i ->
                                DropdownMenuItem(text = { Text("${MESES_LARGOS[i]} 2026", style = sans(14)) }, onClick = { mes = i; menu = false })
                            }
                        }
                    }
                }
                BoxField("SUPERFICIE m²", areaTxt, { areaTxt = it }, Modifier.weight(1f), monoFont = true, keyboardType = KeyboardType.Decimal)
            }
            VSpace(12)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("GIRO", style = label(size = 9.5))
                    VSpace(5)
                    Box(Modifier.fillMaxWidth().height(44.dp).background(C.Sand).border(1.5.dp, C.Ink).padding(horizontal = 11.dp), contentAlignment = Alignment.CenterStart) {
                        Text("Tortillería", style = sans(13.5))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("TIPO DE ESPACIO", style = label(size = 9.5))
                    VSpace(5)
                    Segmented(listOf("FIJO", "SEMIFIJO"), tipo, 41.dp) { tipo = it }
                }
            }
            VSpace(14)
            BoxCard {
                Row(Modifier.fillMaxWidth().background(C.Sand).borderBottom().padding(14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("FUNDAMENTO LEGAL", style = label(C.Ink), modifier = Modifier.weight(1f))
                    Tag("LEY_INGRESOS")
                }
                Column(Modifier.padding(14.dp)) {
                    Text("Art. 31 Fracc. I · Ley de Ingresos Municipal 2026", style = sans(13.5, FontWeight.Bold, lh = 1.4))
                    VSpace(8)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("CUOTA_BASE_MAS_EXCEDENTE", style = mono(11.5, color = C.Body))
                        Text("MENSUAL", style = mono(11.5, color = C.Body))
                    }
                }
            }
            VSpace(16)
            SectionTitle("FÓRMULA PASO A PASO")
            VSpace(10)
            BoxCard {
                pasos.forEachIndexed { i, (t, f) ->
                    Row(Modifier.fillMaxWidth().then(if (i < pasos.lastIndex) Modifier.borderBottom(1.dp, C.Line) else Modifier).padding(14.dp, 12.dp)) {
                        Box(Modifier.size(24.dp).background(C.Ink), contentAlignment = Alignment.Center) { Text("${i + 1}", style = mono(11, color = Color.White)) }
                        HSpace(12)
                        Column(Modifier.weight(1f)) {
                            Text(t, style = sans(12.5, color = C.Body, lh = 1.4))
                            VSpace(4)
                            Text(f, style = mono(13, FontWeight.Medium))
                        }
                    }
                }
            }
            VSpace(12)
            Column(Modifier.fillMaxWidth().background(C.Sand).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RN-FIS-01 · VALOR BASE MENSUAL → DIARIO", style = label(C.Muted2, 10))
                VSpace(10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cuota diaria = ", style = mono(13.5))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${f2(totalUma)} × ${f2(UMA)} × 12", style = mono(13.5), modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 3.dp))
                        Box(Modifier.width(170.dp).height(1.5.dp).background(C.Ink))
                        Text("365", style = mono(13.5), modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            VSpace(12)
            Row(Modifier.fillMaxWidth().background(C.Ink).padding(18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("CUOTA POR UNIDAD · ${MESES_LARGOS[mes].uppercase()}", style = label(C.Muted))
                    VSpace(5)
                    Text("Mensual ${money(mensual)}", style = sans(12, color = C.Gray))
                }
                Text(money(diaria), style = mono(30, color = Color.White, lh = 1))
                Text("/día", style = mono(13, color = C.Muted), modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            }
            VSpace(10)
            Text("Montos calculados con la Ley de Ingresos vigente. Ningún cobro puede exceder esta cuota.", style = sans(11.5, color = C.Muted2, lh = 1.5))
        }
    }
}

/** Vista 2.5.A · Bóveda de comprobantes (pago + pago_detalle). */
@Composable
fun RecibosScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val list = app.pagosDe(LOCAL_LOCATARIO)
    val total = list.filter { !it.cancelado }.sumOf { it.monto }
    Column(Modifier.fillMaxSize()) {
        SimpleBar("pago · fecha_pago DESC", "Mis recibos", avatar = "MG")
        Box(Modifier.background(Color.White).borderBottom()) {
            MetricRow(border = false) {
                MetricCell("PAGADO EN 2026", money(total), Modifier.weight(1f).fillMaxHeight(), vs = 18)
                VLine()
                MetricCell("COMPROBANTES", "${list.size}", Modifier.weight(1f).fillMaxHeight(), vs = 18)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { p -> ReciboTile(p) { nav.push(Route.Comprobante(p.folio, locatario = true)) } }
        }
    }
}
