package mx.gestamercado.data

import android.content.Context
import mx.gestamercado.api.Terminal

/**
 * Configuración persistente de la terminal POS: dispositivoId, prefijo de folios, secreto HMAC
 * y el último consecutivo usado (los folios no deben repetirse entre reinicios).
 * Nota: para producción conviene mover el secreto a EncryptedSharedPreferences / Android Keystore.
 */
object Prefs {
    private const val ARCHIVO = "gesta_terminal"

    fun cargar(ctx: Context, app: AppState) {
        val p = ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        val id = p.getString("dispositivoId", null)
        if (id != null) {
            app.terminal = Terminal(id, p.getString("serieFolio", "") ?: "", p.getString("secretoHmac", "") ?: "", p.getString("alias", null), p.getString("modelo", null))
        }
        val c = p.getInt("consecutivo", -1)
        if (c > 0) app.consecutivo = c
    }

    fun guardarTerminal(ctx: Context, t: Terminal?, consecutivo: Int) {
        ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().apply {
            if (t == null) clear() else {
                putString("dispositivoId", t.dispositivoId)
                putString("serieFolio", t.serieFolio)
                putString("secretoHmac", t.secretoHmac)
                putString("alias", t.alias)
                putString("modelo", t.modelo)
                putInt("consecutivo", consecutivo)
            }
        }.apply()
    }

    fun guardarConsecutivo(ctx: Context, consecutivo: Int) {
        ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().putInt("consecutivo", consecutivo).apply()
    }
}
