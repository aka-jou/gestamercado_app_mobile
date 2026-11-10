package mx.gestamercado.screens.locatario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Route
import mx.gestamercado.ui.*

private val RE_CURP = Regex("^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$")
private val RE_RFC = Regex("^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$")
private val PADRON = setOf("HEOG780412MPLRRD05", "HEOG780412KJ3")

/** Vista 2.1.A · Activación de cuenta de locatario (CU-03 / RN-PAD-01). */
@Composable
fun ActivacionScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val toast = LocalToast.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var tipo by remember { mutableIntStateOf(0) }
    var ident by remember { mutableStateOf("HEOG780412MPLRRD05") }
    var user by remember { mutableStateOf("lupita.hernandez@correo.mx") }
    var pass by remember { mutableStateOf("Tortilla2026") }
    var aviso by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    val v = ident.trim().uppercase()
    val valid = (if (tipo == 0) RE_CURP else RE_RFC).matches(v)
    val fuerza = listOf(pass.length >= 8, pass.any { it.isUpperCase() }, pass.any { it.isDigit() }, pass.any { !it.isLetterOrDigit() } || pass.length >= 12).count { it }

    fun activar() {
        if (!valid) { toast(if (tipo == 0) "La CURP debe tener 18 caracteres con formato válido." else "El RFC debe tener 12 o 13 caracteres con formato válido."); return }
        if (user.isBlank() || pass.length < 8) { toast("Captura un usuario y una contraseña de al menos 8 caracteres."); return }
        if (!aviso) { toast("Debes aceptar el aviso de privacidad."); return }
        focus.clearFocus()
        loading = true
        scope.launch {
            delay(800)
            loading = false
            if (!PADRON.contains(v)) {
                nav.push(Route.NoEmpadronado(v, tipo == 0))
            } else {
                app.locTab = 0
                nav.resetTo(Route.Roles, Route.LocShell)
                toast("Cuenta activada. Bienvenida, María Guadalupe.")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(C.Ink).clipToBounds().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp)) {
            DecoDiamond(190.dp, C.Teal, Alignment.TopEnd, 70.dp, (-50).dp)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SquareBack { nav.pop() }
                    HSpace(12)
                    Mark(34.dp)
                    HSpace(10)
                    Wordmark(19f)
                }
                VSpace(18)
                Text("Activa tu cuenta de locatario", style = sans(26, FontWeight.ExtraBold, Color.White, -0.7, 1.05))
                VSpace(8)
                Text("Vinculamos tu identidad del padrón con un acceso móvil.", style = sans(13.5, color = C.Gray, lh = 1.5))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 24.dp)) {
            SectionTitle("1 · IDENTIDAD FISCAL")
            VSpace(14)
            Segmented(listOf("PERSONA FÍSICA", "PERSONA MORAL"), tipo) {
                tipo = it
                ident = if (it == 0) "HEOG780412MPLRRD05" else "MER190301AB2"
            }
            VSpace(14)
            BoxField(
                if (tipo == 0) "CURP" else "RFC", ident, { ident = it.uppercase() },
                monoFont = true, caps = KeyboardCapitalization.Characters, maxLength = if (tipo == 0) 18 else 13,
                titleTrailing = { Text("${v.length} / ${if (tipo == 0) "18" else "12–13"}", style = mono(10.5, color = if (valid) C.TealDark else C.Muted)) },
                suffix = if (v.isEmpty()) null else ({
                    Box(Modifier.size(20.dp).background(if (valid) C.Teal else C.Magenta), contentAlignment = Alignment.Center) {
                        Icon(if (valid) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }),
                helper = if (v.isEmpty()) null else ({
                    Text(if (valid) "Formato válido" else "Formato inválido", style = sans(11.5, FontWeight.SemiBold, if (valid) C.TealDark else C.Magenta))
                }),
            )
            VSpace(22)
            SectionTitle("2 · CREDENCIALES")
            VSpace(14)
            BoxField("USUARIO O CORREO", user, { user = it }, keyboardType = KeyboardType.Email)
            VSpace(14)
            BoxField(
                "CONTRASEÑA NUEVA", pass, { pass = it }, obscure = !show,
                suffix = { TextButton(onClick = { show = !show }) { Text(if (show) "Ocultar" else "Mostrar", style = sans(12, FontWeight.Bold, C.TealDark)) } },
                helper = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { i ->
                            val c = if (i < fuerza) (if (fuerza <= 1) C.Magenta else if (fuerza == 2) C.Yellow else C.Teal) else C.GrayTint
                            Box(Modifier.weight(1f).height(4.dp).background(c))
                        }
                    }
                },
            )
            VSpace(16)
            Row(Modifier.clickable { aviso = !aviso }) {
                Box(Modifier.size(22.dp).background(if (aviso) C.Ink else Color.White).border(1.5.dp, C.Ink), contentAlignment = Alignment.Center) {
                    if (aviso) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                HSpace(12)
                Text(
                    buildAnnotatedString {
                        append("Acepto el ")
                        withStyle(SpanStyle(color = C.TealDark, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append("aviso de privacidad") }
                        append(" para el tratamiento de mis datos personales.")
                    },
                    style = sans(13, color = C.Body, lh = 1.5),
                    modifier = Modifier.weight(1f),
                )
            }
            VSpace(22)
            PrimaryButton(if (loading) "Validando contra padrón…" else "Activar cuenta", if (loading) null else ({ activar() }), arrow = true)
            VSpace(12)
            TextButton(onClick = { app.locTab = 0; nav.resetTo(Route.Roles, Route.LocShell) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    buildAnnotatedString {
                        append("¿Ya tienes cuenta? ")
                        withStyle(SpanStyle(color = C.TealDark, fontWeight = FontWeight.Bold)) { append("Inicia sesión") }
                    },
                    style = sans(13, color = C.Body),
                )
            }
            VSpace(4)
            Text("Prueba: cambia un carácter de la CURP para ver el flujo alterno de persona no empadronada.", textAlign = TextAlign.Center, style = sans(11.5, color = C.Muted, lh = 1.5), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Vista 2.1.A · Flujo alterno: la CURP/RFC no existe en persona. */
@Composable
fun NoEmpadronadoScreen(ident: String, esCurp: Boolean) {
    val nav = LocalNav.current
    val toast = LocalToast.current
    val doc = if (esCurp) "CURP" else "RFC"
    Column(Modifier.fillMaxSize()) {
        SimpleBar("ACTIVACIÓN · CU-03", "Sin coincidencia en el padrón", back = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Diamond(20.dp, C.Teal, hollow = true, stroke = 3.dp)
                Diamond(20.dp, C.Magenta)
                Diamond(20.dp, C.Teal, hollow = true, stroke = 3.dp)
            }
            VSpace(20)
            Text("$doc NO ENCONTRADA EN PERSONA", style = label(C.Magenta, 11))
            VSpace(10)
            Text("Aún no estás en el padrón de comerciantes", style = sans(30, FontWeight.ExtraBold, ls = -0.9, lh = 1.05))
            VSpace(10)
            Text("La cuenta móvil solo puede vincularse a una persona ya inscrita. Acude a la Dirección de Mercados para tu empadronamiento inicial.", style = sans(14, color = C.Body, lh = 1.55))
            VSpace(20)
            BoxCard(padding = PaddingValues(14.dp, 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ident, style = mono(14), modifier = Modifier.weight(1f))
                    Text("SIN COINCIDENCIA", style = label(C.Magenta, 10))
                }
            }
            VSpace(14)
            Column(Modifier.fillMaxWidth().background(C.Ink).padding(18.dp)) {
                Text("DIRECCIÓN DE MERCADOS", style = label(C.Muted))
                VSpace(8)
                Text("Palacio Municipal, planta baja · ventanilla 4", style = sans(15, FontWeight.Bold, Color.White, lh = 1.35))
                VSpace(6)
                Text("Lunes a viernes · 9:00 a 15:00", style = sans(13, color = C.Gray))
                VSpace(14)
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.BarBorder))
                VSpace(14)
                Text("LLEVA CONTIGO", style = label(C.Muted))
                VSpace(8)
                listOf("Identificación oficial vigente", "CURP impresa o RFC con homoclave", "Comprobante de domicilio reciente").forEach { r ->
                    Row(Modifier.padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Diamond(7.dp, C.Teal)
                        HSpace(10)
                        Text(r, style = sans(13, color = Color.White))
                    }
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            PrimaryButton("Intentar con otra $doc", { nav.pop() }, color = C.Ink)
            VSpace(8)
            GhostButton("Cómo llegar", { toast("Abriendo mapas: Palacio Municipal, ventanilla 4.") }, height = 52.dp)
        }
    }
}
