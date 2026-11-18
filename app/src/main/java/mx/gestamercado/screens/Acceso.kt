package mx.gestamercado.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalBackend
import mx.gestamercado.LocalNav
import mx.gestamercado.LocalToast
import mx.gestamercado.Nav
import mx.gestamercado.Route
import mx.gestamercado.api.ApiError
import mx.gestamercado.api.Backend
import mx.gestamercado.api.CambiarPasswordRequest
import mx.gestamercado.api.LoginRequest
import mx.gestamercado.api.Perfil
import mx.gestamercado.api.Terminal
import mx.gestamercado.data.AppState
import mx.gestamercado.data.CUENTAS_DEMO
import mx.gestamercado.data.Prefs
import mx.gestamercado.data.Remoto
import mx.gestamercado.ui.*

/**
 * Tras autenticar (y cambiar la contraseña si hacía falta) decide el perfil:
 * LOCATARIO → «Mi local»; INSPECTOR / ADMINISTRADOR → terminal enrolada + puede-abrir-jornada.
 * Devuelve un mensaje de error o null.
 */
suspend fun continuarSesion(app: AppState, backend: Backend, nav: Nav): String? = try {
    when (app.perfil) {
        Perfil.LOCATARIO -> {
            runCatching { Remoto.cargarMisLocales(app, backend) }
            nav.resetTo(Route.Login, Route.LocShell)
            null
        }
        Perfil.INSPECTOR -> {
            if (app.terminal == null) {
                nav.resetTo(Route.Login, Route.Terminal)
                null
            } else {
                // M-I1 · GET /api/v1/cortes-caja/puede-abrir-jornada?cobradorId={uuid}&mercadoId={id}
                val j = withContext(Dispatchers.IO) { backend.puedeAbrirJornada(app.usuarioId, app.mercadoId) }
                app.jornada = j
                if (!j.puedeAbrir) nav.resetTo(Route.Login, Route.Bloqueo)
                else {
                    runCatching { Remoto.cargarPadron(app, backend) }
                    nav.resetTo(Route.Login, Route.InspShell)
                }
                null
            }
        }
        null -> "Tu rol no tiene acceso a la app móvil (solo LOCATARIO, INSPECTOR o ADMINISTRADOR)."
    }
} catch (e: ApiError) {
    if (e.sinRed) "Se necesita conexión para validar la apertura de jornada." else e.message
}

/** Vista M-L1 / M-I1 · Inicio de sesión (POST /api/v1/auth/login). */
@Composable
fun LoginScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val backend = LocalBackend.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(if (app.simulado) "insp.jramirez" else "") }
    var pass by remember { mutableStateOf(if (app.simulado) "demo1234" else "") }
    var show by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun entrar() {
        val u = user.trim()
        if (u.isEmpty() || pass.isEmpty()) { error = "Captura usuario y contraseña."; return }
        focus.clearFocus()
        error = null
        loading = true
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { backend.login(LoginRequest(u, pass)) }
                if (r.perfil == null) {
                    error = "Tu rol (${r.roles.joinToString()}) no tiene acceso a la app móvil."
                } else {
                    app.iniciarSesion(r)
                    if (r.debeCambiarPass) nav.resetTo(Route.Login, Route.CambiarPassword)
                    else error = continuarSesion(app, backend, nav)
                }
            } catch (e: ApiError) {
                error = if (e.sinRed) "Sin conexión con el servidor." else e.message
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().background(C.Ink).clipToBounds().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 26.dp)) {
            DecoDiamond(220.dp, C.Teal, Alignment.TopEnd, 90.dp, (-80).dp)
            Column {
                if (app.simulado) {
                    StatusChip(if (app.online) "EN LÍNEA" else "OFFLINE", "modo simulado", if (app.online) C.Teal else C.Yellow) { app.online = !app.online }
                    VSpace(18)
                }
                Mark(52.dp)
                VSpace(18)
                Wordmark(40f)
                VSpace(10)
                Text("LOCATARIOS · INSPECTORES EN CAMPO", style = label(C.Teal, 11))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Iniciar sesión", style = sans(24, FontWeight.ExtraBold, ls = -0.6))
            VSpace(6)
            Text("La app abre el perfil de locatario o de inspector según tu rol.", style = sans(14, color = C.Body, lh = 1.5))
            VSpace(18)
            BoxField("USUARIO", user, { user = it; error = null }, monoFont = true, keyboardType = KeyboardType.Email)
            VSpace(14)
            BoxField(
                "CONTRASEÑA", pass, { pass = it; error = null }, obscure = !show,
                suffix = { TextButton(onClick = { show = !show }) { Text(if (show) "Ocultar" else "Mostrar", style = sans(12, FontWeight.Bold, C.TealDark)) } },
            )
            error?.let {
                VSpace(12)
                Notice(it, tone = Tone.DANGER)
            }
            VSpace(18)
            PrimaryButton(if (loading) "Validando…" else "Entrar", if (loading) null else ({ entrar() }), arrow = true)
            VSpace(8)
            Text("POST /api/v1/auth/login · el inspector valida además puede-abrir-jornada.", style = sans(12, color = C.Muted2, lh = 1.5))
            if (app.simulado) {
                VSpace(20)
                Text("CUENTAS DE PRUEBA · CUALQUIER CONTRASEÑA", style = label())
                VSpace(8)
                CUENTAS_DEMO.forEach { c ->
                    GhostButton("${c.username}\n${c.descripcion}", { user = c.username; pass = "demo1234"; error = null }, height = 52.dp)
                    VSpace(8)
                }
            }
        }
        val t = app.terminal
        Row(
            Modifier.fillMaxWidth().background(C.Sand).borderTop().clickable { nav.push(Route.Terminal) }.navigationBarsPadding().padding(24.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (t == null) {
                Text("TERMINAL POS SIN ENROLAR · toca para configurarla", style = label(C.Magenta, 10.5), modifier = Modifier.weight(1f))
            } else {
                listOf("SERIE FOLIO" to t.serieFolio, "MODELO" to (t.modelo ?: "—"), "ALIAS" to (t.alias ?: "—")).forEach { (k, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(k, style = label(C.Muted2, 9.5))
                        VSpace(3)
                        Text(v, style = mono(12.5), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Vista M-L1 · Cambio obligatorio de contraseña (POST /api/v1/auth/cambiar-password). */
@Composable
fun CambiarPasswordScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val backend = LocalBackend.current
    val toast = LocalToast.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirma by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val fuerza = listOf(nueva.length >= 8, nueva.any { it.isUpperCase() }, nueva.any { it.isDigit() }, nueva.any { !it.isLetterOrDigit() } || nueva.length >= 12).count { it }

    fun guardar() {
        val r = CambiarPasswordRequest(actual, nueva)
        val errores = r.validar() + listOfNotNull(if (actual.isEmpty()) "Captura la contraseña temporal." else null, if (nueva != confirma) "La confirmación no coincide." else null)
        if (errores.isNotEmpty()) { error = errores.joinToString(" "); return }
        focus.clearFocus()
        loading = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { backend.cambiarPassword(r) }
                toast("Contraseña actualizada.")
                error = continuarSesion(app, backend, nav)
            } catch (e: ApiError) {
                error = if (e.sinRed) "Sin conexión con el servidor." else e.message
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleBar("PRIMER ACCESO · debeCambiarPass", "Crea tu contraseña")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Notice("Tu cuenta se creó con una contraseña temporal. Antes de continuar define una propia de al menos 8 caracteres.", title = "HOLA, ${app.nombre.uppercase()}")
            VSpace(18)
            BoxField("CONTRASEÑA TEMPORAL", actual, { actual = it; error = null }, obscure = true)
            VSpace(14)
            BoxField(
                "CONTRASEÑA NUEVA", nueva, { nueva = it; error = null }, obscure = true,
                helper = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) { i ->
                            val c = if (i < fuerza) (if (fuerza <= 1) C.Magenta else if (fuerza == 2) C.Yellow else C.Teal) else C.GrayTint
                            Box(Modifier.weight(1f).height(4.dp).background(c))
                        }
                    }
                },
            )
            VSpace(14)
            BoxField("CONFIRMAR CONTRASEÑA", confirma, { confirma = it; error = null }, obscure = true)
            error?.let {
                VSpace(12)
                Notice(it, tone = Tone.DANGER)
            }
            VSpace(20)
            PrimaryButton(if (loading) "Guardando…" else "Guardar y continuar", if (loading) null else ({ guardar() }), arrow = true)
            VSpace(8)
            GhostButton("Cancelar y salir", { app.cerrarSesion(); nav.resetTo(Route.Login) })
        }
    }
}

/** Datos de la terminal POS enrolada desde la web (W5): dispositivoId, serie de folios y secreto HMAC. */
@Composable
fun TerminalScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val backend = LocalBackend.current
    val toast = LocalToast.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = app.terminal
    var id by remember { mutableStateOf(t?.dispositivoId ?: "") }
    var serie by remember { mutableStateOf(t?.serieFolio ?: "") }
    var secreto by remember { mutableStateOf(t?.secretoHmac ?: "") }
    var alias by remember { mutableStateOf(t?.alias ?: "") }
    var modelo by remember { mutableStateOf(t?.modelo ?: "Sunmi V2 PRO") }
    var siguiente by remember { mutableStateOf(app.consecutivo.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var verSecreto by remember { mutableStateOf(false) }
    val enSesion = app.perfil == Perfil.INSPECTOR

    fun guardar() {
        val nuevo = Terminal(id.trim().lowercase(), serie.trim(), secreto.trim(), alias.trim().ifEmpty { null }, modelo.trim().ifEmpty { null })
        val n = siguiente.trim().toIntOrNull()
        val errores = nuevo.validar() + listOfNotNull(if (n == null || n < 1) "El siguiente folio debe ser un número ≥ 1." else null)
        if (errores.isNotEmpty()) { error = errores.joinToString(" "); return }
        app.terminal = nuevo
        app.consecutivo = n!!
        Prefs.guardarTerminal(ctx, nuevo, n)
        toast("Terminal ${nuevo.serieFolio} guardada.")
        if (enSesion) scope.launch { error = continuarSesion(app, backend, nav) } else nav.pop()
    }

    Column(Modifier.fillMaxSize()) {
        SimpleBar("ENROLAMIENTO · POST /api/v1/dispositivos (WEB W5)", "Terminal POS", back = !enSesion)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Notice("Copia los datos que mostró el panel web al enrolar esta terminal. El secreto HMAC firma cada cobro offline (hashOffline) y solo se muestra una vez.", title = "DATOS DEL ENROLAMIENTO")
            VSpace(18)
            BoxField("DISPOSITIVO ID (UUID)", id, { id = it; error = null }, monoFont = true, hint = "9b1deb4d-3b7d-4bad-…")
            VSpace(14)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoxField("PREFIJO DE SERIE", serie, { serie = it.uppercase().filter { c -> c.isLetterOrDigit() }; error = null }, Modifier.weight(1f), monoFont = true, caps = KeyboardCapitalization.Characters, maxLength = 10)
                BoxField("SIGUIENTE FOLIO", siguiente, { siguiente = it.filter { c -> c.isDigit() }; error = null }, Modifier.weight(1f), monoFont = true, keyboardType = KeyboardType.Number)
            }
            VSpace(6)
            Text("Folios: ${serie.ifBlank { "SERIE" }}-${(siguiente.toIntOrNull() ?: 1).toString().padStart(6, '0')}, …", style = mono(11.5, color = C.Muted2))
            VSpace(14)
            BoxField(
                "SECRETO HMAC", secreto, { secreto = it.trim(); error = null }, monoFont = true, obscure = !verSecreto,
                suffix = { TextButton(onClick = { verSecreto = !verSecreto }) { Text(if (verSecreto) "Ocultar" else "Mostrar", style = sans(12, FontWeight.Bold, C.TealDark)) } },
            )
            VSpace(14)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoxField("ALIAS", alias, { alias = it }, Modifier.weight(1f))
                BoxField("MODELO", modelo, { modelo = it }, Modifier.weight(1f))
            }
            error?.let {
                VSpace(12)
                Notice(it, tone = Tone.DANGER)
            }
            VSpace(20)
            PrimaryButton("Guardar terminal", { guardar() }, arrow = enSesion)
            if (enSesion) {
                VSpace(8)
                GhostButton("Cerrar sesión", { app.cerrarSesion(); nav.resetTo(Route.Login) })
            }
        }
    }
}
