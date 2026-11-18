package mx.gestamercado.screens.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalBackend
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Route
import mx.gestamercado.api.Firma
import mx.gestamercado.api.FormaPago
import mx.gestamercado.api.Giro
import mx.gestamercado.api.PersonaBreve
import mx.gestamercado.data.Gps
import mx.gestamercado.data.LocalStatus
import mx.gestamercado.ui.*
import mx.gestamercado.util.*

/** Vista M-I4 · Permiso temporal de feria express cobrado en campo (POST /api/v1/concesiones/feria-express). */
@Composable
fun FeriaExpressScreen(localInicial: String?) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val toast = LocalToast.current
    val backend = LocalBackend.current
    val ctx = LocalContext.current
    val focus = LocalFocusManager.current

    val espacios = app.locales.filter { it.base == LocalStatus.DESOCUPADO && app.permisoVigente(it.id) == null }.sortedBy { if (it.esFeria) 0 else 1 }
    var localId by remember { mutableStateOf(localInicial ?: espacios.firstOrNull()?.id) }
    var q by remember { mutableStateOf("") }
    var personas by remember { mutableStateOf<List<PersonaBreve>>(emptyList()) }
    var persona by remember { mutableStateOf<PersonaBreve?>(null) }
    var giros by remember { mutableStateOf<List<Giro>>(emptyList()) }
    var giro by remember { mutableStateOf<Giro?>(null) }
    var offset by remember { mutableIntStateOf(0) }
    var dias by remember { mutableIntStateOf(3) }
    var forma by remember { mutableIntStateOf(0) }
    var ubicacion by remember { mutableStateOf(Gps.posicion(ctx, app.simulado)) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { giros = runCatching { withContext(Dispatchers.IO) { backend.giros() } }.getOrDefault(emptyList()) }
    LaunchedEffect(q) {
        delay(250)
        personas = runCatching { withContext(Dispatchers.IO) { backend.buscarPersonas(q) } }.getOrDefault(emptyList()).take(6)
    }

    val local = localId?.let { app.localOrNull(it) }
    val inicio = app.hoy.plusDays(offset.toLong())
    val sugerido = round2((local?.cuota ?: 0.0) * dias)
    var montoTxt by remember(localId, dias) { mutableStateOf(f2(sugerido)) }
    val monto = montoTxt.replace(",", "").trim().toDoubleOrNull() ?: 0.0
    val t = app.terminal

    fun expedir() {
        val l = local ?: run { error = "Selecciona el espacio de feria."; return }
        val p = persona ?: run { error = "Selecciona al comerciante."; return }
        val g = giro ?: run { error = "Selecciona el giro."; return }
        focus.clearFocus()
        try {
            val pago = app.expedirFeria(l, p.id, p.nombre, g.id, g.nombre, inicio, dias, round2(monto), FormaPago.entries[forma], ubicacion)
            nav.replace(Route.Comprobante(pago.folio))
            toast("Permiso ${pago.folio} expedido · ${l.id} hasta ${fDia(inicio.plusDays((dias - 1).toLong()))}.")
        } catch (e: IllegalStateException) {
            error = e.message
        }
    }

    Column(Modifier.fillMaxSize()) {
        InspectorBar("FERIA EXPRESS · PERMISO TEMPORAL", "Expedir permiso", back = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("ESPACIO / ZONA DE FERIA · localId", style = label())
            VSpace(6)
            if (espacios.isEmpty()) Notice("No hay espacios libres para permisos temporales.", tone = Tone.WARN)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                espacios.forEach { e -> SelectChip("${e.id} · ${f2(e.area)} m²", localId == e.id, if (e.esFeria) C.Teal else C.Gray) { localId = e.id; error = null } }
            }
            if (local != null) {
                VSpace(6)
                Text("${local.zona} · cuota ${money(local.cuota)}/día", style = sans(12, color = C.Muted2))
            }

            VSpace(16)
            Text("COMERCIANTE / FERIANTE · titularPersonaId", style = label())
            VSpace(6)
            val sel = persona
            if (sel != null) {
                BoxCard(padding = PaddingValues(12.dp, 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sel.nombre, style = sans(14, FontWeight.Bold))
                            Text(sel.curp ?: corto(sel.id, 8, 4), style = mono(11.5, color = C.Muted2))
                        }
                        Text("Cambiar", style = sans(12, FontWeight.Bold, C.TealDark), modifier = Modifier.clickable { persona = null })
                    }
                }
            } else {
                BoxField("BUSCAR POR NOMBRE O CURP", q, { q = it }, hint = "Nombre, razón social o CURP")
                VSpace(6)
                BoxCard {
                    if (personas.isEmpty()) Text("Sin coincidencias en el padrón de personas.", style = sans(12.5, color = C.Muted2), modifier = Modifier.padding(12.dp))
                    personas.forEachIndexed { i, p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { persona = p; error = null }.then(if (i < personas.lastIndex) Modifier.borderBottom(1.dp, C.Line) else Modifier).padding(12.dp, 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Diamond(8.dp, C.Teal, hollow = true)
                            HSpace(9)
                            Column(Modifier.weight(1f)) {
                                Text(p.nombre, style = sans(13.5, FontWeight.Bold))
                                if (p.curp != null) Text(p.curp, style = mono(11, color = C.Muted2))
                            }
                        }
                    }
                }
            }

            VSpace(16)
            Text("GIRO COMERCIAL · giroId", style = label())
            VSpace(6)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                giros.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        fila.forEach { g -> Box(Modifier.weight(1f)) { SelectChip(g.nombre, giro?.id == g.id) { giro = g; error = null } } }
                        if (fila.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            VSpace(16)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Contador("FECHA DE INICIO", if (offset == 0) "Hoy" else fDia(inicio), Modifier.weight(1f), offset > 0, offset < 30, { offset-- }, { offset++ })
                Contador("DURACIÓN · 1 A 30", "$dias ${if (dias == 1) "día" else "días"}", Modifier.weight(1f), dias > 1, dias < 30, { dias-- }, { dias++ })
            }
            VSpace(6)
            Text("Vigencia: ${fDia(inicio)} – ${fDia(inicio.plusDays((dias - 1).toLong()))}", style = mono(11.5, color = C.Muted2))

            VSpace(16)
            BoxField(
                "MONTO COBRADO", montoTxt, { montoTxt = it }, monoFont = true, prefixText = "\$ ", borderColor = C.Magenta, keyboardType = KeyboardType.Decimal,
                helper = { Text("Sugerido: ${money(local?.cuota ?: 0.0)} × $dias días = ${money(sugerido)}", style = sans(11.5, color = C.Muted2)) },
            )
            VSpace(14)
            Text("FORMA DE PAGO", style = label())
            VSpace(6)
            Segmented(listOf("EFECTIVO", "TRANSFER.", "TARJETA"), forma) { forma = it }
            VSpace(14)
            BoxCard(padding = PaddingValues(12.dp, 10.dp), onClick = { ubicacion = Gps.posicion(ctx, app.simulado) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Diamond(9.dp, if (ubicacion != null) C.Teal else C.Yellow)
                    HSpace(9)
                    Text("GPS · ${Gps.texto(ubicacion)}", style = mono(12), modifier = Modifier.weight(1f))
                }
            }
            error?.let {
                VSpace(12)
                Notice(it, tone = Tone.DANGER)
            }
            VSpace(14)
            if (t != null) Text("Folio ${Firma.folio(t.serieFolio, app.consecutivo)} · firma HMAC-SHA256 de la terminal", style = mono(11, color = C.Muted2))
            VSpace(10)
            PrimaryButton("Cobrar y expedir permiso", if (monto >= 0.01 && t != null && !app.jornadaCerrada) ({ expedir() }) else null, color = C.TealDark, amount = money(monto))
            VSpace(8)
        }
    }
}

@Composable
private fun Contador(titulo: String, valor: String, modifier: Modifier, menos: Boolean, mas: Boolean, onMenos: () -> Unit, onMas: () -> Unit) {
    Column(modifier) {
        Text(titulo, style = label(size = 9.5))
        VSpace(6)
        Row(Modifier.fillMaxWidth().height(52.dp).background(Color.White).border(1.5.dp, C.Ink), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(38.dp).fillMaxHeight().clickable(enabled = menos, onClick = onMenos), contentAlignment = Alignment.Center) {
                Text("−", style = sans(20, FontWeight.Bold, if (menos) C.Ink else C.Gray))
            }
            Text(valor, style = mono(13.5), textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            Box(Modifier.width(38.dp).fillMaxHeight().clickable(enabled = mas, onClick = onMas), contentAlignment = Alignment.Center) {
                Text("+", style = sans(20, FontWeight.Bold, if (mas) C.Ink else C.Gray))
            }
        }
    }
}
