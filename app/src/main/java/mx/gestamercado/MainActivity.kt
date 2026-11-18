package mx.gestamercado

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.gestamercado.api.Backend
import mx.gestamercado.api.GestaApi
import mx.gestamercado.api.Perfil
import mx.gestamercado.data.AppState
import mx.gestamercado.data.Prefs
import mx.gestamercado.data.Simulado
import mx.gestamercado.data.sincronizar
import mx.gestamercado.screens.*
import mx.gestamercado.screens.inspector.*
import mx.gestamercado.screens.locatario.*
import mx.gestamercado.ui.C
import mx.gestamercado.ui.GestaTheme
import mx.gestamercado.ui.sans

sealed interface Route {
    /** M-L1 / M-I1 · inicio de sesión único; el perfil se decide por roles. */
    data object Login : Route
    data object CambiarPassword : Route
    data object Terminal : Route
    data object Bloqueo : Route
    data object InspShell : Route
    data class Cobro(val id: String) : Route
    data class Comprobante(val folio: String, val locatario: Boolean = false) : Route
    data class Historial(val id: String) : Route
    data class Asistencia(val id: String) : Route
    data object AltaLocal : Route
    data class Feria(val localId: String? = null) : Route
    data object LocShell : Route
}

class Nav {
    val stack = mutableStateListOf<Route>(Route.Login)
    val current: Route get() = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(r: Route) { stack[stack.lastIndex] = r }
    fun resetTo(vararg rs: Route) { stack.clear(); stack.addAll(rs) }
}

val LocalApp = staticCompositionLocalOf<AppState> { error("AppState no disponible") }
val LocalNav = staticCompositionLocalOf<Nav> { error("Nav no disponible") }
val LocalToast = staticCompositionLocalOf<(String) -> Unit> { {} }
val LocalBackend = staticCompositionLocalOf<Backend> { error("Backend no disponible") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AColor.WHITE, AColor.BLACK),
        )
        setContent { GestaApp() }
    }
}

@Composable
fun GestaApp() {
    val ctx = LocalContext.current
    val app = remember { AppState(simulado = BuildConfig.API_URL.isBlank()).also { Prefs.cargar(ctx, it) } }
    val backend: Backend = remember { if (app.simulado) Simulado(app) else GestaApi(BuildConfig.API_URL) }
    val nav = remember { Nav() }
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { msg ->
        scope.launch {
            host.currentSnackbarData?.dismiss()
            host.showSnackbar(msg)
        }
    }

    // Los folios no se repiten entre reinicios: se guarda el consecutivo de la terminal.
    LaunchedEffect(app.consecutivo) { Prefs.guardarConsecutivo(ctx, app.consecutivo) }

    // Cola offline: al haber red y registros pendientes se suben solos (M-I2, M-I3, M-I4).
    LaunchedEffect(app.online, app.pendientes, app.perfil) {
        if (app.perfil == Perfil.INSPECTOR && app.online && app.pendientes > 0) {
            delay(1500)
            val r = sincronizar(app, backend)
            if (r.error != null && !r.sinRed && r.enviados == 0) toast(r.error)
        }
    }

    GestaTheme {
        CompositionLocalProvider(LocalApp provides app, LocalNav provides nav, LocalToast provides toast, LocalBackend provides backend) {
            Box(Modifier.fillMaxSize().background(C.Bg).imePadding()) {
                BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
                when (val r = nav.current) {
                    Route.Login -> LoginScreen()
                    Route.CambiarPassword -> CambiarPasswordScreen()
                    Route.Terminal -> TerminalScreen()
                    Route.Bloqueo -> BloqueoScreen()
                    Route.InspShell -> InspectorShell()
                    is Route.Cobro -> CobroScreen(r.id)
                    is Route.Comprobante -> ComprobanteScreen(r.folio, r.locatario)
                    is Route.Historial -> HistorialScreen(r.id)
                    is Route.Asistencia -> AsistenciaScreen(r.id)
                    Route.AltaLocal -> AltaLocalScreen()
                    is Route.Feria -> FeriaExpressScreen(r.localId)
                    Route.LocShell -> LocatarioShell()
                }
                SnackbarHost(host, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 76.dp)) { d ->
                    Snackbar(shape = RectangleShape, containerColor = C.Ink, contentColor = Color.White) {
                        androidx.compose.material3.Text(d.visuals.message, style = sans(13.5, color = Color.White, lh = 1.4))
                    }
                }
            }
        }
    }
}
