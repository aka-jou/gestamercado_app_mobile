package mx.gestamercado.screens.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Route
import mx.gestamercado.data.SyncState
import mx.gestamercado.ui.*
import mx.gestamercado.util.*

/** Vista 1.6.A · Monitor de sincronización (CU-19). */
@Composable
fun SyncScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val all = app.colaTurno
    val enCola = all.filter { it.sync == SyncState.EN_COLA }
    val rev = all.filter { it.sync == SyncState.REVISION }
    val list = when (tab) { 1 -> enCola; 2 -> rev; else -> all }

    fun sync() {
        if (!app.online) { toast("Sin red. Toca OFFLINE en la barra superior para simular conexión."); return }
        busy = true
        scope.launch {
            delay(1000)
            val n = app.sincronizar()
            busy = false
            toast(if (n == 0) "No había registros en cola." else "$n registros enviados al servidor.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        InspectorBar("COLA OFFLINE · SQLITE", "Sincronización", actions = { Avatar("JR") })
        Box(Modifier.borderBottom()) {
            MetricRow(border = false) {
                MetricCell("ÚLTIMA SINCRONIZACIÓN", fHora(app.lastSync), Modifier.weight(1f).fillMaxHeight(), sub = fDia(app.lastSync))
                VLine()
                val p = app.pendientes
                MetricCell(
                    "SIN SUBIR", "$p", Modifier.weight(1f).fillMaxHeight(), sub = "sincronizado_en IS NULL",
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
                SelectChip("Todos ${all.size}", tab == 0) { tab = 0 }
                SelectChip("En cola ${enCola.size}", tab == 1) { tab = 1 }
                SelectChip("Revisión ${rev.size}", tab == 2, fg = C.Magenta) { tab = 2 }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) Text("Nada por aquí.", style = sans(14, color = C.Muted2), modifier = Modifier.padding(20.dp))
            list.forEach { p ->
                val estado = when (p.sync) {
                    SyncState.EN_COLA -> "EN COLA"
                    SyncState.SINCRONIZADO -> "SINCRONIZADO"
                    SyncState.REVISION -> "REQUIERE REVISIÓN"
                }
                BoxCard(padding = PaddingValues(12.dp, 10.dp), onClick = { nav.push(Route.Comprobante(p.folio)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.folio, style = mono(12.5, FontWeight.Medium), modifier = Modifier.weight(1f))
                        TagEstado(estado)
                    }
                    VSpace(6)
                    Row {
                        Text("${p.localId} · ${fCorta(p.fecha)}", style = sans(12, color = C.Body), modifier = Modifier.weight(1f))
                        Text(money(p.monto), style = mono(12.5))
                    }
                    VSpace(4)
                    Text("hash ${corto(p.hash)}", style = mono(10.5, color = C.Muted))
                    if (p.revision != null) {
                        VSpace(4)
                        Text(p.revision, style = sans(11.5, FontWeight.SemiBold, C.Magenta))
                    }
                }
            }
        }
    }
}

/** Vista 1.6.B · Resumen de turno y precorte de caja. */
@Composable
fun CorteScreen() {
    val app = LocalApp.current
    Column(Modifier.fillMaxSize()) {
        InspectorBar("v_pagos_sin_corte", "Precorte de caja", actions = { Avatar("JR") })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("DÍA OPERATIVO", style = label(size = 10))
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
            HeroAmount("EFECTIVO EXIGIBLE · ENTREGAR EN TESORERÍA", money(app.efectivoTurno), "${app.cobrosEfectivo} cobros · forma_pago = EFECTIVO · sin corte_caja_id")
            VSpace(12)
            MetricRow {
                MetricCell("TRANSFERENCIA · ${app.cobrosTransfer}", money(app.transferTurno), Modifier.weight(1f).fillMaxHeight())
                VLine()
                MetricCell("TARJETA · ${app.cobrosTarjeta}", money(app.tarjetaTurno), Modifier.weight(1f).fillMaxHeight())
            }
            VSpace(10)
            Text("Montos informativos: no se entregan en efectivo. El cierre lo ejecuta Tesorería con fn_cerrar_corte.", style = sans(12, color = C.Muted2, lh = 1.5))
            if (app.pendientes > 0) {
                VSpace(12)
                Notice("Tienes ${app.pendientes} cobros sin sincronizar. Súbelos antes de acudir a Tesorería.", tone = Tone.WARN)
            }
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
            VSpace(8)
            Text("Historial inmutable · corte_caja_diario", style = sans(11.5, color = C.Muted))
        }
    }
}
