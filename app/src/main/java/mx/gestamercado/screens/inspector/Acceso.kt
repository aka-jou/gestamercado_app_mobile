package mx.gestamercado.screens.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalBackend
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Route
import mx.gestamercado.screens.continuarSesion
import mx.gestamercado.ui.*
import mx.gestamercado.util.money

/** Vista M-I1 · Jornada bloqueada: puede-abrir-jornada = false (corte ciego pendiente, RN-FIS-05). */
@Composable
fun BloqueoScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val backend = LocalBackend.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val j = app.jornada
    val pendientes = j?.pendientes.orEmpty()
    val total = pendientes.sumOf { it.efectivo }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(C.Magenta).clipToBounds().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 26.dp)) {
            DecoDiamond(180.dp, Color.White, Alignment.BottomEnd, 60.dp, 80.dp)
            Column {
                Box(Modifier.size(46.dp).background(C.Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                VSpace(16)
                Text("JORNADA BLOQUEADA · puede-abrir-jornada = false", style = label(C.PinkText, 10.5))
                VSpace(10)
                Text("Tienes efectivo sin entregar a Tesorería", style = sans(28, FontWeight.ExtraBold, Color.White, -0.8, 1.05))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Notice(
                j?.motivo ?: "El inspector debe liquidar la recaudación de cada jornada antes de iniciar la siguiente. El mapa y el cobro permanecen deshabilitados hasta que Tesorería registre el corte.",
                title = "ART. 23 FRACC. XVIII · REGLAMENTO DE MERCADOS",
            )
            VSpace(18)
            Text("INSPECTOR", style = label())
            VSpace(4)
            Text(app.nombre, style = sans(17, FontWeight.Bold))
            VSpace(2)
            Text("${app.sesion?.username ?: ""} · mercadoId ${app.mercadoId}", style = sans(12.5, color = C.Muted2))
            if (pendientes.isNotEmpty()) {
                VSpace(16)
                BoxCard {
                    Row(Modifier.fillMaxWidth().background(C.Ink).padding(14.dp, 10.dp)) {
                        Text("JORNADA", style = label(Color.White, 10), modifier = Modifier.weight(1f))
                        Text("PAGOS", style = label(Color.White, 10), textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                        Text("EFECTIVO", style = label(Color.White, 10), textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
                    }
                    pendientes.forEach { p ->
                        Row(Modifier.fillMaxWidth().borderBottom(1.dp, C.Line).padding(14.dp, 12.dp)) {
                            Text(p.fecha, style = mono(13.5), modifier = Modifier.weight(1f))
                            Text("${p.pagos}", style = mono(13.5), textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
                            Text(money(p.efectivo), style = mono(13.5), textAlign = TextAlign.End, modifier = Modifier.width(110.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().background(C.Pink).padding(14.dp, 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("TOTAL A ENTREGAR", style = label(C.Magenta, 11.5), modifier = Modifier.weight(1f))
                        Text(money(total), style = mono(17, FontWeight.Medium, C.Magenta))
                    }
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            PrimaryButton(if (checking) "Validando…" else "Volver a validar", if (checking) null else ({
                checking = true
                scope.launch {
                    val err = continuarSesion(app, backend, nav)
                    checking = false
                    when {
                        err != null -> toast(err)
                        app.jornada?.puedeAbrir == false -> toast("Tesorería aún no registra el corte. La jornada sigue bloqueada.")
                    }
                }
            }), color = C.Ink)
            VSpace(8)
            GhostButton("Cerrar sesión", { app.cerrarSesion(); nav.resetTo(Route.Login) }, height = 52.dp)
        }
    }
}
