package mx.gestamercado.screens.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalBackend
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Route
import mx.gestamercado.api.ApiError
import mx.gestamercado.api.CerrarCorteRequest
import mx.gestamercado.api.dinero
import mx.gestamercado.data.SyncState
import mx.gestamercado.data.sincronizar
import mx.gestamercado.ui.*
import mx.gestamercado.util.*

private fun estadoSync(s: SyncState) = when (s) {
    SyncState.EN_COLA -> "EN COLA"
    SyncState.SINCRONIZADO -> "SINCRONIZADO"
    SyncState.REVISION -> "REQUIERE REVISIÓN"
}

/** Monitor de la cola offline: cobros firmados, permisos de feria y pases de lista. */
@Composable
fun SyncScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val toast = LocalToast.current
    val backend = LocalBackend.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val all = app.colaTurno
    val enCola = all.filter { it.sync == SyncState.EN_COLA }
    val rev = all.filter { it.sync == SyncState.REVISION }
    val list = when (tab) { 1 -> enCola; 2 -> rev; else -> all }
    val asis = app.asistencias.filter { tab == 0 || (tab == 1 && it.sync == SyncState.EN_COLA) || (tab == 2 && it.sync == SyncState.REVISION) }

    fun sync() {
        if (!app.online) { toast(if (app.simulado) "Sin red. Toca OFFLINE en la barra superior para simular conexión." else "Sin red: se reintentará al recuperar señal."); return }
        busy = true
        scope.launch {
            val r = sincronizar(app, backend)
            busy = false
            toast(
                when {
                    r.sinRed -> "Se perdió la conexión: ${r.enviados} enviados, el resto sigue en cola."
                    r.error != null -> r.error
                    r.enviados == 0 && r.revision == 0 -> "No había registros en cola."
                    else -> "${r.enviados} registros enviados" + if (r.revision > 0) " · ${r.revision} a revisión." else "."
                },
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        InspectorBar("COLA OFFLINE · HMAC-SHA256", "Sincronización", actions = { Avatar(app.iniciales) })
        Box(Modifier.borderBottom()) {
            MetricRow(border = false) {
                MetricCell("ÚLTIMA SINCRONIZACIÓN", fHora(app.lastSync), Modifier.weight(1f).fillMaxHeight(), sub = fDia(app.lastSync))
                VLine()
                val p = app.pendientes
                MetricCell(
                    "SIN SUBIR", "$p", Modifier.weight(1f).fillMaxHeight(), sub = "máx. ${app.diasMaxAntiguedadCobro} días offline",
                    bg = if (p > 0) C.YellowTint else C.TealTint, tc = if (p > 0) C.YellowText else C.TealDark, vs = 24,
                )
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
            PrimaryButton(
                if (busy) "Sincronizando…" else if (app.online) "Sincronizar ahora" else "Sincronizar al recuperar red",
                if (busy) null else ({ sync() }), color = C.Ink, height = 48.dp,
            )
            VSpace(12)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectChip("Todos ${all.size + app.asistencias.size}", tab == 0) { tab = 0 }
                SelectChip("En cola ${app.pendientes}", tab == 1) { tab = 1 }
                SelectChip("Revisión ${rev.size + app.asistencias.count { it.sync == SyncState.REVISION }}", tab == 2, fg = C.Magenta) { tab = 2 }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty() && asis.isEmpty()) Text("Nada por aquí.", style = sans(14, color = C.Muted2), modifier = Modifier.padding(20.dp))
            list.forEach { p ->
                BoxCard(padding = PaddingValues(12.dp, 10.dp), onClick = { nav.push(Route.Comprobante(p.folio)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.folio, style = mono(12.5, FontWeight.Medium), modifier = Modifier.weight(1f))
                        TagEstado(estadoSync(p.sync))
                    }
                    VSpace(6)
                    Row {
                        Text("${if (p.canal == "FERIA_EXPRESS") "FERIA · " else ""}${p.localId} · ${fCorta(p.fecha)}", style = sans(12, color = C.Body), modifier = Modifier.weight(1f))
                        Text(money(p.monto), style = mono(12.5))
                    }
                    VSpace(4)
                    Text("hashOffline ${corto(p.hash)}", style = mono(10.5, color = C.Muted))
                    p.revision?.let {
                        VSpace(4)
                        Text(it, style = sans(11.5, FontWeight.SemiBold, C.Magenta))
                    }
                }
            }
            if (asis.isNotEmpty()) {
                VSpace(6)
                SectionTitle("PASES DE LISTA · /api/v1/asistencias")
                asis.forEach { a ->
                    BoxCard(padding = PaddingValues(12.dp, 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${a.localId} · ${a.tipo.name}", style = mono(12, FontWeight.Medium), modifier = Modifier.weight(1f))
                            TagEstado(estadoSync(a.sync))
                        }
                        VSpace(4)
                        Text("${fCorta(a.fecha)} · ${mx.gestamercado.data.Gps.texto(a.ubicacion)}", style = mono(10.5, color = C.Muted))
                        a.revision?.let { Text(it, style = sans(11.5, FontWeight.SemiBold, C.Magenta)) }
                    }
                }
            }
        }
    }
}

private val DENOMINACIONES = listOf(1000.0, 500.0, 200.0, 100.0, 50.0, 20.0, 10.0, 5.0, 2.0, 1.0, 0.5)

/** Vista M-I5 · Cierre de jornada con corte ciego (POST /api/v1/cortes-caja/cerrar). */
@Composable
fun CorteScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val toast = LocalToast.current
    val backend = LocalBackend.current
    val scope = rememberCoroutineScope()
    val conteo = remember { mutableStateMapOf<Double, Int>() }
    var montoTxt by remember { mutableStateOf("") }
    var obs by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sumaConteo = round2(conteo.entries.sumOf { it.key * it.value })
    val monto = montoTxt.replace(",", "").trim().toDoubleOrNull()
    val corte = app.ultimoCorte

    fun cerrar() {
        val req = CerrarCorteRequest(app.usuarioId, app.mercadoId, app.hoy, dinero(monto ?: -1.0), obs)
        val errores = req.validar()
        if (errores.isNotEmpty()) { error = errores.joinToString(" "); return }
        enviando = true
        error = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { backend.cerrarCorte(req) }
                app.registrarCorte(r)
                toast("Corte ${r.id ?: ""} registrado.")
            } catch (e: ApiError) {
                error = if (e.sinRed) "Se necesita conexión para cerrar el corte." else e.message
            } finally {
                enviando = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        InspectorBar("CORTE CIEGO · /cortes-caja/cerrar", "Cierre de jornada", actions = { BarAction("Salir") { app.cerrarSesion(); nav.resetTo(Route.Login) } })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("FECHA DE LA JORNADA · fechaCorte", style = label(size = 10))
                    VSpace(3)
                    Text(fDiaLargo(app.hoy), style = sans(18, FontWeight.ExtraBold))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("COBROS", style = label(size = 10))
                    VSpace(3)
                    Text("${app.cobrosTurno}", style = mono(18))
                }
            }
            VSpace(14)
            if (corte != null) {
                val dif = corte.diferencia ?: 0.0
                HeroAmount(
                    "CORTE ${corte.id ?: ""} · ${corte.estado ?: "CERRADO"}", money(corte.montoDeclarado),
                    "Efectivo declarado" + (corte.montoEsperado?.let { " · sistema ${money(it)}" } ?: ""),
                    if (kotlin.math.abs(dif) < 0.01) C.TealDark else C.Magenta,
                )
                VSpace(12)
                Notice(
                    when {
                        corte.diferencia == null -> "Tesorería conciliará el corte."
                        kotlin.math.abs(dif) < 0.01 -> "El efectivo cuadra con lo cobrado."
                        dif > 0 -> "Sobrante de ${money(dif)}."
                        else -> "Faltante de ${money(-dif)}."
                    },
                    "JORNADA CERRADA", if (kotlin.math.abs(dif) < 0.01) Tone.OK else Tone.WARN,
                )
                VSpace(12)
                PrimaryButton("Cerrar sesión", { app.cerrarSesion(); nav.resetTo(Route.Login) }, color = C.Ink)
            } else {
                Notice("Cuenta el efectivo físico y declara el total sin ver lo que registró el sistema. La diferencia se conoce al cerrar.", "CORTE CIEGO · RN-FIS-05")
                if (app.pendientes > 0) {
                    VSpace(10)
                    Notice("Tienes ${app.pendientes} registros sin sincronizar. Súbelos antes de cerrar el corte.", tone = Tone.DANGER)
                }
                VSpace(16)
                SectionTitle("CONTEO DE BILLETES Y MONEDAS")
                VSpace(10)
                BoxCard {
                    DENOMINACIONES.chunked(2).forEachIndexed { i, fila ->
                        Row(Modifier.fillMaxWidth().then(if (i < 5) Modifier.borderBottom(1.dp, C.Line) else Modifier).height(IntrinsicSize.Min)) {
                            fila.forEachIndexed { j, d ->
                                if (j > 0) VLine()
                                Denominacion(d, conteo[d] ?: 0, Modifier.weight(1f)) { n ->
                                    if (n <= 0) conteo.remove(d) else conteo[d] = n
                                    montoTxt = f2(round2(conteo.entries.sumOf { it.key * it.value }))
                                }
                            }
                            if (fila.size == 1) { VLine(); Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                VSpace(6)
                Text("Suma del conteo: ${money(sumaConteo)}", style = mono(12, color = C.Muted2))
                VSpace(14)
                BoxField("EFECTIVO FÍSICO CONTADO · montoDeclarado", montoTxt, { montoTxt = it; error = null }, monoFont = true, prefixText = "\$ ", borderColor = C.Magenta, keyboardType = KeyboardType.Decimal)
                VSpace(14)
                BoxField("OBSERVACIONES DEL ARQUEO", obs, { obs = it }, minLines = 2, hint = "Justificación en caso de sobrante o faltante")
                error?.let {
                    VSpace(12)
                    Notice(it, tone = Tone.DANGER)
                }
                VSpace(16)
                PrimaryButton(
                    if (enviando) "Cerrando…" else "Cerrar jornada",
                    if (monto != null && monto >= 0 && app.pendientes == 0 && !enviando) ({ confirmar = true }) else null,
                    amount = monto?.let { money(it) },
                )
            }
            if (app.cortes.isNotEmpty()) {
                VSpace(18)
                SectionTitle("CORTES CERRADOS")
                VSpace(10)
                BoxCard {
                    app.cortes.forEachIndexed { i, c ->
                        Column(Modifier.fillMaxWidth().then(if (i < app.cortes.lastIndex) Modifier.borderBottom(1.dp, C.Line) else Modifier).padding(13.dp, 11.dp)) {
                            Row {
                                Text(c.id, style = mono(12.5, FontWeight.Medium), modifier = Modifier.weight(1f))
                                Text(money(c.monto), style = mono(14))
                            }
                            VSpace(5)
                            Row {
                                Text("${c.fecha} · sec. ${c.secuencia} · ${c.pagos} pagos", style = sans(11.5, color = C.Muted2), modifier = Modifier.weight(1f))
                                Text("liq. ${c.liquidado}", style = sans(11.5, color = C.Muted2))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            shape = RectangleShape,
            containerColor = Color.White,
            title = { Text("¿Declarar ${money(monto ?: 0.0)}?", style = sans(18, FontWeight.ExtraBold)) },
            text = { Text("El corte ciego no se puede modificar. Al cerrarlo la jornada queda bloqueada hasta que Tesorería lo liquide.", style = sans(14, color = C.Body, lh = 1.5)) },
            confirmButton = { TextButton(onClick = { confirmar = false; cerrar() }) { Text("Cerrar jornada", style = sans(14, FontWeight.Bold, C.Magenta)) } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Revisar", style = sans(14, FontWeight.Bold, C.Ink)) } },
        )
    }
}

@Composable
private fun Denominacion(valor: Double, n: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Row(modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (valor < 1) "50¢" else "\$${valor.toInt()}", style = mono(12.5, FontWeight.Medium), modifier = Modifier.width(50.dp))
        Box(Modifier.size(30.dp).border(1.dp, C.Ink).clickable(enabled = n > 0) { onChange(n - 1) }, contentAlignment = Alignment.Center) {
            Text("−", style = sans(16, FontWeight.Bold, if (n > 0) C.Ink else C.Gray))
        }
        Text("$n", style = mono(13), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Box(Modifier.size(30.dp).background(C.Ink).clickable { onChange(n + 1) }, contentAlignment = Alignment.Center) {
            Text("+", style = sans(16, FontWeight.Bold, Color.White))
        }
    }
}
