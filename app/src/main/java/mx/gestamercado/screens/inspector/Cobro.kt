package mx.gestamercado.screens.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Route
import mx.gestamercado.data.Aplicacion
import mx.gestamercado.data.SyncState
import mx.gestamercado.ui.*
import mx.gestamercado.util.*

/** Vista 1.3.A · Pantalla de liquidación y captura de cobro (FIFO). */
@Composable
fun CobroScreen(localId: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val focus = LocalFocusManager.current
    val l = app.local(localId)
    val yaGenerado = app.generadoHoy(l.id)
    var unidades by remember { mutableIntStateOf(if (yaGenerado) 0 else 1) }
    var montoTxt by remember {
        val total = round2(app.adeudo(l.id) + if (!yaGenerado) l.cuota else 0.0)
        mutableStateOf(f2(if (l.id == "A-08") 200.0 else total))
    }
    var forma by remember { mutableIntStateOf(0) }
    val pagadores = listOf("${l.titular} · TITULAR") + l.suplentes.map { "${it.nombre} · ${it.parentesco}" }
    var pagador by remember { mutableIntStateOf(if (l.suplentes.isNotEmpty()) 1 else 0) }
    val formas = listOf("EFECTIVO", "TRANSFERENCIA", "TARJETA")
    val monto = montoTxt.replace(",", "").replace("\$", "").trim().toDoubleOrNull() ?: 0.0
    val previa = app.simular(l.id, monto, unidades)
    val pend = app.cargosDe(l.id).filter { it.saldo > 0 }

    Column(Modifier.fillMaxSize()) {
        InspectorBar("COBRO · ${l.id} · ${l.tarjeton}", l.nombre, back = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            BoxCard {
                Row(Modifier.fillMaxWidth().background(C.Sand).borderBottom().padding(14.dp, 10.dp)) {
                    Text("TARIFA APLICABLE", style = label(C.Ink), modifier = Modifier.weight(1f))
                    Text("v_local_cuota_actual", style = mono(10.5, color = C.Muted2))
                }
                Column(Modifier.padding(14.dp)) {
                    Celda("MÉTODO", "CUOTA_BASE_MAS_EXCEDENTE")
                    VSpace(10)
                    Row {
                        Celda("PERIODICIDAD", "DIARIA", Modifier.weight(1f))
                        Celda("VALOR", "${f2(umaMes(l.tipo, l.area))} UMA/mes", Modifier.weight(1f))
                    }
                    VSpace(10)
                    Row {
                        Celda("UMA APLICADA", money(UMA), Modifier.weight(1f))
                        Celda("CUOTA POR UNIDAD", "${money(l.cuota)} / día", Modifier.weight(1f), C.TealDark, 14)
                    }
                }
            }
            VSpace(18)
            SectionTitle("PASIVOS · FIFO")
            VSpace(10)
            if (pend.isEmpty() && (unidades == 0 || yaGenerado)) {
                Notice("Sin cargos pendientes. Cualquier monto irá a saldo a favor.", tone = Tone.OK)
                VSpace(8)
            }
            pend.forEachIndexed { i, c ->
                Pasivo(
                    "${if (c.concepto == "RECARGO") "RECARGO 1.25%" else c.concepto} #${c.id}",
                    if (c.concepto == "RECARGO") C.Magenta else C.Ink,
                    c.periodo, c.total, c.pagado, c.saldo, previa.items.getOrNull(i),
                )
            }
            if (unidades > 0 && !yaGenerado) {
                val t = round2(l.cuota * unidades)
                Pasivo("DERECHO_PISO · al vuelo", C.TealDark, "${fDia(app.hoy)} · $unidades u · se genera al confirmar", t, 0.0, t, previa.items.getOrNull(pend.size))
            }
            Row(Modifier.padding(horizontal = 13.dp)) {
                Text("TOTAL", style = label(size = 9.5), modifier = Modifier.weight(1f))
                Text("PAGADO PREVIO", style = label(size = 9.5), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("SALDO", style = label(size = 9.5), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            VSpace(18)
            SectionTitle("CAPTURA")
            VSpace(12)
            Row {
                Column(Modifier.width(128.dp)) {
                    Text("UNIDADES", style = label())
                    VSpace(6)
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).background(if (yaGenerado) C.Sand else Color.White).border(1.5.dp, C.Ink),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Paso("−", !yaGenerado && unidades > 0) { unidades-- }
                        Text("$unidades", style = mono(16), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Paso("+", !yaGenerado && unidades < 31) { unidades++ }
                    }
                }
                HSpace(10)
                BoxField(
                    "MONTO RECIBIDO", montoTxt, { montoTxt = it }, Modifier.weight(1f),
                    monoFont = true, prefixText = "\$ ", borderColor = C.Magenta, keyboardType = KeyboardType.Decimal,
                )
            }
            if (yaGenerado) {
                VSpace(6)
                Text("El cargo de hoy ya se generó en un cobro anterior.", style = sans(11.5, color = C.Muted2))
            }
            VSpace(14)
            Text("FORMA DE PAGO", style = label())
            VSpace(6)
            Segmented(listOf("EFECTIVO", "TRANSFER.", "TARJETA"), forma) { forma = it }
            VSpace(14)
            Text("QUIÉN PAGA · persona_pagador_id", style = label())
            VSpace(6)
            BoxCard {
                pagadores.forEachIndexed { i, p ->
                    val sel = i == pagador
                    Row(
                        Modifier.fillMaxWidth().background(if (sel) C.TealTint else Color.White).clickable { pagador = i }
                            .then(if (i < pagadores.lastIndex) Modifier.borderBottom(1.dp, C.Line) else Modifier).padding(14.dp, 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Diamond(12.dp, if (sel) C.TealDark else C.Ink, hollow = !sel)
                        HSpace(10)
                        Text(p, style = sans(13.5, if (sel) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f))
                    }
                }
            }
            VSpace(16)
            Column(Modifier.fillMaxWidth().background(C.Ink).padding(16.dp, 14.dp)) {
                Text("PREVISUALIZACIÓN · pago_detalle", style = label(C.Muted))
                VSpace(9)
                if (previa.items.all { it.monto == 0.0 }) Text("Captura un monto para ver la aplicación.", style = sans(12.5, color = C.Gray))
                previa.items.filter { it.monto > 0 }.forEach { a ->
                    Row(Modifier.padding(bottom = 7.dp)) {
                        Text(
                            if (a.resultado == "PARCIAL") "${a.concepto} → PARCIAL · resta ${money(a.resta)}" else "${a.concepto} → PAGADO",
                            style = sans(12.5, color = Color.White), modifier = Modifier.weight(1f),
                        )
                        Text(money(a.monto), style = mono(12.5, color = if (a.resultado == "PARCIAL") C.YellowLight else C.TealLight))
                    }
                }
                Row {
                    Text("Saldo a favor generado", style = sans(12.5, color = Color.White), modifier = Modifier.weight(1f))
                    Text(money(previa.favor), style = mono(12.5, color = if (previa.favor > 0) C.TealLight else C.Gray))
                }
            }
            VSpace(16)
            PrimaryButton("Confirmar cobro", if (monto > 0) ({
                focus.clearFocus()
                val p = app.cobrar(l, round2(monto), unidades, formas[forma], pagadores[pagador])
                nav.replace(Route.Comprobante(p.folio))
            }) else null, amount = money(monto))
            VSpace(8)
        }
    }
}

@Composable
private fun Celda(t: String, v: String, modifier: Modifier = Modifier, color: Color = C.Ink, size: Number = 12.5) {
    Column(modifier) {
        Text(t, style = label(size = 9.5))
        VSpace(3)
        Text(v, style = mono(size, color = color))
    }
}

@Composable
private fun Paso(t: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(38.dp).fillMaxHeight().clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, style = sans(20, FontWeight.Bold, if (enabled) C.Ink else C.Gray))
    }
}

@Composable
private fun Pasivo(concepto: String, cc: Color, periodo: String, total: Double, pagado: Double, saldo: Double, ap: Aplicacion?) {
    val res = ap?.resultado ?: "PENDIENTE"
    val (bg, fg) = estadoTag(res)
    BoxCard(Modifier.padding(bottom = 8.dp), padding = PaddingValues(12.dp, 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(concepto, style = mono(11.5, FontWeight.Medium, cc), modifier = Modifier.weight(1f))
            Tag(if (res == "PENDIENTE") "PENDIENTE" else "→ $res", bg, fg)
        }
        VSpace(6)
        Text(periodo, style = sans(12.5, color = C.Body))
        VSpace(7)
        Row {
            Text(money(total), style = mono(12, color = C.Muted2), modifier = Modifier.weight(1f))
            Text(money(pagado), style = mono(12, color = C.Muted2), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text(money(saldo), style = mono(12, FontWeight.Medium), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

/** Vista 1.3.B (inspector) y detalle 2.5.A (locatario): comprobante digital / ticket ESC/POS. */
@Composable
fun ComprobanteScreen(folio: String, locatario: Boolean) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val p = app.pago(folio)
    val l = app.local(p.localId)
    var printing by remember { mutableStateOf(false) }

    fun imprimir(re: Boolean) {
        if (!app.printer) { toast("Impresora no enlazada. Toca ESC/POS en la barra superior para enlazarla."); return }
        printing = true
        scope.launch {
            delay(1200)
            printing = false
            toast(if (re) "Reimpresión enviada a XP-P323B (marcada como COPIA)." else "Ticket enviado a XP-P323B por Bluetooth.")
        }
    }

    @Composable
    fun R(k: String, v: String) {
        Row(Modifier.padding(bottom = 5.dp)) {
            Text(k, style = mono(11.5, color = C.Muted2))
            HSpace(10)
            Text(v, style = mono(11.5), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }

    Column(Modifier.fillMaxSize().background(C.Sand)) {
        if (locatario) SimpleBar("RECIBO · ${if (p.cancelado) "CANCELADO" else "VIGENTE"}", p.folio, back = true)
        else InspectorBar("COBRO REGISTRADO EN ROOM", "Comprobante digital", back = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp, 18.dp)) {
            if (p.cancelado) {
                Notice(p.motivo ?: "Pago cancelado", "CANCELADO", Tone.DANGER)
                VSpace(12)
            }
            BoxCard(padding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mark(30.dp, negative = false)
                    HSpace(10)
                    Column {
                        Text("H. Ayuntamiento de Santa Lucía", style = sans(13, FontWeight.Bold))
                        Text("Mercado Poniente · MP-01", style = sans(11, color = C.Muted2))
                    }
                }
                VSpace(12); DashedLine(); VSpace(12)
                R("FOLIO", p.folio)
                R("UUID", corto(p.uuid, 8, 4))
                R("LOCAL", fTicket(p.fecha))
                R("UTC", fTicket(p.fecha.plusHours(6)))
                if (locatario) {
                    R("COBRÓ", p.cobrador)
                    R("PAGÓ", p.pagador.substringBefore(" · "))
                }
                VSpace(7); DashedLine(); VSpace(12)
                Text("${l.id} · ${l.nombre}", style = mono(11.5, FontWeight.Medium))
                VSpace(4)
                listOf("Titular: ${l.titular}", "Giro: ${l.giroAutorizado}", "Art. 31 Fr. I Ley de Ingresos 2026", "UMA ${money(UMA)} · ${f2(l.area)} m²").forEach {
                    Text(it, style = mono(11.5, color = C.Body))
                }
                VSpace(12); DashedLine(); VSpace(12)
                if (locatario) {
                    Text("CONCEPTOS LIQUIDADOS · monto_aplicado", style = label(size = 9.5))
                    VSpace(8)
                }
                if (p.detalle.isEmpty()) R("Pago aplicado por FIFO", money(p.monto))
                p.detalle.forEach { a -> R(if (a.resultado == "PARCIAL") "${a.concepto} (parcial)" else a.concepto, money(a.monto)) }
                if (p.favor > 0) R("Saldo a favor", money(p.favor))
                VSpace(6)
                Box(Modifier.fillMaxWidth().height(1.5.dp).background(C.Ink))
                VSpace(8)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("TOTAL · ${p.formaPago}", style = mono(12, FontWeight.Medium), modifier = Modifier.weight(1f))
                    Text(money(p.monto), style = mono(20, FontWeight.Medium))
                }
                VSpace(14)
                Row {
                    QrCode("${p.uuid}|${p.folio}|${f2(p.monto)}|${p.hash}", 100.dp)
                    HSpace(12)
                    Column(Modifier.weight(1f)) {
                        Text(if (locatario) "SELLO SHA-256" else "HASH_OFFLINE · SHA-256", style = label(size = 9.5))
                        VSpace(4)
                        Text(if (locatario) p.hash else "${p.hash.take(10)}…${p.hash.takeLast(8)}", style = mono(10, color = C.Body, lh = 1.4))
                        VSpace(8)
                        Text("QR", style = label(size = 9.5))
                        VSpace(2)
                        Text("uuid · folio · monto · hash", style = mono(10, color = C.Body))
                    }
                }
            }
            VSpace(12)
            if (locatario) Notice("Autenticidad verificable al escanear el QR", tone = Tone.OK)
            else Notice(if (p.gps) "GPS capturado · 19.04141, −98.20634 · ±6 m" else "Cobro sin señal GPS: se marcará para revisión.", tone = if (p.gps) Tone.OK else Tone.WARN)
            if (!locatario && p.sync == SyncState.EN_COLA) {
                VSpace(8)
                Notice("Guardado en el teléfono. Se subirá al recuperar señal (${app.pendientes} en cola).", tone = Tone.WARN)
            }
        }
        Column(Modifier.navigationBarsPadding().padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 16.dp)) {
            if (locatario) {
                PrimaryButton("Descargar comprobante PDF", { toast("Comprobante ${p.folio}.pdf guardado en Descargas.") })
                VSpace(8)
                GhostButton("Compartir comprobante", { toast("Abriendo opciones para compartir…") }, fill = Color.White)
            } else {
                PrimaryButton(if (printing) "Imprimiendo…" else "Imprimir ticket Bluetooth", if (printing) null else ({ imprimir(false) }))
                VSpace(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Reimprimir", if (printing) null else ({ imprimir(true) }), Modifier.weight(1f), fill = Color.White)
                    GhostButton("PDF / WhatsApp", { toast("Generando PDF para compartir por WhatsApp…") }, Modifier.weight(1f), fill = Color.White)
                }
                TextButton(onClick = { nav.pop() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Volver a la ronda", style = sans(14, FontWeight.Bold, C.TealDark))
                }
            }
        }
    }
}

@Composable
fun HistorialScreen(localId: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val l = app.local(localId)
    val list = app.pagosDe(localId)
    Column(Modifier.fillMaxSize()) {
        InspectorBar("HISTORIAL DE PAGOS · ${l.id}", l.nombre, back = true)
        if (list.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Sin pagos registrados para este local.", style = sans(14, color = C.Muted2), textAlign = TextAlign.Center)
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { p -> ReciboTile(p) { nav.push(Route.Comprobante(p.folio)) } }
            }
        }
    }
}
