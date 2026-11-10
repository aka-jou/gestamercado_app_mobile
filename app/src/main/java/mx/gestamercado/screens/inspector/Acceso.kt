package mx.gestamercado.screens.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Route
import mx.gestamercado.ui.*

/** Vista 1.1.A · Login operativo (CU-01). */
@Composable
fun InspectorLogin() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf("insp.jramirez") }
    var pass by remember { mutableStateOf("demo1234") }
    var show by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun enter() {
        val u = user.trim().lowercase()
        if (u.isEmpty() || pass.isEmpty()) { error = "Captura usuario y contraseña."; return }
        if (u != "insp.jramirez" && u != "insp.lmorales") { error = "Usuario no registrado en este dispositivo. Usa uno de prueba."; return }
        focus.clearFocus()
        error = null
        loading = true
        scope.launch {
            delay(700)
            loading = false
            app.inspector = u
            if (u == "insp.lmorales") nav.push(Route.Bloqueo) else nav.replace(Route.InspShell)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(C.Ink).clipToBounds().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 26.dp)) {
            DecoDiamond(210.dp, C.Teal, Alignment.TopEnd, 80.dp, (-30).dp)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SquareBack { nav.pop() }
                    HSpace(10)
                    StatusChip(if (app.online) "EN LÍNEA" else "OFFLINE", null, if (app.online) C.Teal else C.Yellow) { app.online = !app.online }
                    HSpace(6)
                    StatusChip("ESC/POS", if (app.printer) "XP-P323B" else "sin enlazar", if (app.printer) C.Teal else C.Muted, !app.printer) { app.printer = !app.printer }
                }
                VSpace(24)
                Mark(52.dp)
                VSpace(18)
                Wordmark(40f)
                VSpace(10)
                Text("APP DE CAMPO · INSPECTOR FISCAL", style = label(C.Teal, 11))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Iniciar jornada", style = sans(24, FontWeight.ExtraBold, ls = -0.6))
            VSpace(6)
            Text("Antes de abrir el mapa validamos que no tengas efectivo pendiente de corte.", style = sans(14, color = C.Body, lh = 1.5))
            VSpace(18)
            BoxField("USUARIO", user, { user = it }, monoFont = true)
            VSpace(14)
            BoxField(
                "CONTRASEÑA", pass, { pass = it }, obscure = !show,
                suffix = { TextButton(onClick = { show = !show }) { Text(if (show) "Ocultar" else "Mostrar", style = sans(12, FontWeight.Bold, C.TealDark)) } },
            )
            error?.let {
                VSpace(12)
                Notice(it, tone = Tone.DANGER)
            }
            VSpace(18)
            PrimaryButton(if (loading) "Validando jornada…" else "Validar y entrar", if (loading) null else ({ enter() }), arrow = true)
            VSpace(8)
            Text("Se ejecuta fn_puede_abrir_jornada y v_jornadas_sin_liquidar.", style = sans(12, color = C.Muted2, lh = 1.5))
            VSpace(20)
            Text("USUARIOS DE PRUEBA", style = label())
            VSpace(8)
            GhostButton("insp.jramirez · jornada libre", { user = "insp.jramirez"; error = null }, height = 44.dp)
            VSpace(8)
            GhostButton("insp.lmorales · corte pendiente", { user = "insp.lmorales"; error = null }, height = 44.dp)
        }
        Row(Modifier.fillMaxWidth().background(C.Sand).borderTop().navigationBarsPadding().padding(24.dp, 12.dp)) {
            listOf("SERIE FOLIO" to "M01-A9F", "MODELO" to "XCover 7", "ALIAS" to "Poniente 03").forEach { (k, v) ->
                Column(Modifier.weight(1f)) {
                    Text(k, style = label(C.Muted2, 9.5))
                    VSpace(3)
                    Text(v, style = mono(12.5))
                }
            }
        }
    }
}

/** Vista 1.1.B · Bloqueo por corte ciego pendiente (RN-FIS-05). */
@Composable
fun BloqueoScreen() {
    val nav = LocalNav.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val ret = listOf(Triple("05 oct 2026", "14", "\$1,260.00"), Triple("06 oct 2026", "9", "\$810.00"))

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(C.Magenta).clipToBounds().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 26.dp)) {
            DecoDiamond(180.dp, Color.White, Alignment.BottomEnd, 60.dp, 80.dp)
            Column {
                Box(Modifier.size(46.dp).background(C.Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                VSpace(16)
                Text("JORNADA BLOQUEADA · fn_puede_abrir_jornada = false", style = label(C.PinkText, 10.5))
                VSpace(10)
                Text("Tienes efectivo sin entregar a Tesorería", style = sans(28, FontWeight.ExtraBold, Color.White, -0.8, 1.05))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Notice(
                "El inspector debe liquidar la recaudación de cada jornada antes de iniciar la siguiente. El mapa y el cobro permanecen deshabilitados hasta que Tesorería registre el corte.",
                title = "ART. 23 FRACC. XVIII · REGLAMENTO DE MERCADOS",
            )
            VSpace(18)
            Text("MERCADO DONDE ADEUDA LIQUIDACIÓN", style = label())
            VSpace(4)
            Text("Mercado Poniente · MP-01", style = sans(17, FontWeight.Bold))
            VSpace(2)
            Text("Inspector: Luis Morales · insp.lmorales", style = sans(12.5, color = C.Muted2))
            VSpace(16)
            BoxCard {
                Row(Modifier.fillMaxWidth().background(C.Ink).padding(14.dp, 10.dp)) {
                    Text("JORNADA", style = label(Color.White, 10), modifier = Modifier.weight(1f))
                    Text("PAGOS", style = label(Color.White, 10), textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                    Text("EFECTIVO", style = label(Color.White, 10), textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
                }
                ret.forEach { (f, p, m) ->
                    Row(Modifier.fillMaxWidth().borderBottom(1.dp, C.Line).padding(14.dp, 12.dp)) {
                        Text(f, style = mono(13.5), modifier = Modifier.weight(1f))
                        Text(p, style = mono(13.5), textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                        Text(m, style = mono(13.5), textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().background(C.Pink).padding(14.dp, 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL A ENTREGAR", style = label(C.Magenta, 11.5), modifier = Modifier.weight(1f))
                    Text("\$2,070.00", style = mono(17, FontWeight.Medium, C.Magenta))
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            PrimaryButton(if (checking) "Validando…" else "Volver a validar", if (checking) null else ({
                checking = true
                scope.launch {
                    delay(900)
                    checking = false
                    toast("Tesorería aún no registra el corte. La jornada sigue bloqueada.")
                }
            }), color = C.Ink)
            VSpace(8)
            GhostButton("Cerrar sesión", { nav.pop() }, height = 52.dp)
        }
    }
}
