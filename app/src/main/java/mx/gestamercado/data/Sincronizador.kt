package mx.gestamercado.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import mx.gestamercado.api.ApiError
import mx.gestamercado.api.Backend
import mx.gestamercado.api.excedeAntiguedad

data class ResultadoSync(val enviados: Int, val revision: Int, val sinRed: Boolean, val error: String? = null)

/**
 * Envía la cola offline (asistencias, cobros firmados y permisos de feria) en orden cronológico.
 * Reintentar es seguro: el pagoId hace idempotente el cobro (409 = ya estaba en el servidor).
 */
private val candado = Mutex()

suspend fun sincronizar(app: AppState, backend: Backend): ResultadoSync {
    if (!candado.tryLock()) return ResultadoSync(0, 0, false, "Ya hay una sincronización en curso.")
    try { return enviarCola(app, backend) } finally { candado.unlock() }
}

private suspend fun enviarCola(app: AppState, backend: Backend): ResultadoSync {
    var enviados = 0
    var revision = 0
    val ahora = app.instante(app.ahora())
    for (item in app.colaDeEnvio()) {
        val (ruta, cuerpo) = when (item) {
            is Pago -> item.endpoint to item.cuerpo
            is Asistencia -> mx.gestamercado.api.Rutas.ASISTENCIAS to item.cuerpo
            else -> null to null
        }
        // Registros de demostración sin cuerpo: solo existen en modo simulado.
        if (ruta == null || cuerpo == null) {
            if (item is Pago && backend.simulado && app.online) { item.sync = SyncState.SINCRONIZADO; enviados++ }
            continue
        }
        if (item is Pago && excedeAntiguedad(app.instante(item.fecha), ahora, app.diasMaxAntiguedadCobro)) {
            item.sync = SyncState.REVISION
            item.revision = "Excede ${app.diasMaxAntiguedadCobro} días de antigüedad (diasMaxAntiguedadCobro)."
            revision++
            continue
        }
        try {
            withContext(Dispatchers.IO) { backend.enviar(ruta, cuerpo) }
            marcar(item, SyncState.SINCRONIZADO, null)
            enviados++
        } catch (e: ApiError) {
            when {
                e.sinRed -> { if (enviados > 0) app.marcarSincronizado(); return ResultadoSync(enviados, revision, true, e.message) }
                e.duplicado -> { marcar(item, SyncState.SINCRONIZADO, null); enviados++ }
                e.status == 401 -> return ResultadoSync(enviados, revision, false, "Sesión expirada: vuelve a iniciar sesión.")
                else -> { marcar(item, SyncState.REVISION, e.message); revision++ }
            }
        }
    }
    app.marcarSincronizado()
    return ResultadoSync(enviados, revision, false)
}

private fun marcar(item: Any, s: SyncState, motivo: String?) {
    when (item) {
        is Pago -> { item.sync = s; if (motivo != null) item.revision = motivo }
        is Asistencia -> { item.sync = s; item.revision = motivo }
    }
}
