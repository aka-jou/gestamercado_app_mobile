package mx.gestamercado.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.gestamercado.LocalNav
import mx.gestamercado.Route
import mx.gestamercado.ui.*

@Composable
fun RoleChooser() {
    val nav = LocalNav.current
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(C.Ink).clipToBounds().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 36.dp, bottom = 32.dp)) {
            DecoDiamond(220.dp, C.Teal, Alignment.TopEnd, 90.dp, (-80).dp)
            Column {
                Mark(60.dp)
                VSpace(26)
                Wordmark(44f)
                VSpace(12)
                Text("PROTOTIPO · DATOS SIMULADOS", style = label(C.Teal, 11))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("¿Cómo vas a entrar?", style = sans(24, FontWeight.ExtraBold, ls = -0.6))
            VSpace(8)
            Text("Elige el módulo para recorrer todas sus vistas.", style = sans(14, color = C.Body, lh = 1.5))
            VSpace(22)
            RoleTile("1", "Cobrador / Inspector fiscal", "Ronda en mapa, cobro offline, ticket, actas, alta de locales, sincronización y corte.", C.Ink) { nav.push(Route.InspLogin) }
            VSpace(12)
            RoleTile("2", "Locatario", "Activación, tarjetón QR, estado de cuenta, simulador en UMA y recibos.", C.TealDark) { nav.push(Route.Activacion) }
            VSpace(22)
            Text(
                "Los datos son simulados y se reinician al cerrar la app. Un cobro hecho como inspector en el local A-08 aparece en el estado de cuenta y los recibos del locatario.",
                style = sans(12, color = C.Muted2, lh = 1.55),
            )
        }
    }
}

@Composable
private fun RoleTile(code: String, title: String, sub: String, color: Color, onClick: () -> Unit) {
    BoxCard(padding = PaddingValues(16.dp), onClick = onClick) {
        Row {
            Box(Modifier.size(44.dp).background(color), contentAlignment = Alignment.Center) { Text(code, style = mono(18, color = Color.White)) }
            HSpace(14)
            Column(Modifier.weight(1f)) {
                Text(title, style = sans(16, FontWeight.ExtraBold))
                VSpace(4)
                Text(sub, style = sans(12.5, color = C.Body, lh = 1.45))
            }
            HSpace(8)
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = C.Ink, modifier = Modifier.padding(top = 10.dp).size(20.dp))
        }
    }
}
