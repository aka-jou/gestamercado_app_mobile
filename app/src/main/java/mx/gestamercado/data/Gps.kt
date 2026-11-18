package mx.gestamercado.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import mx.gestamercado.api.GeoPoint
import java.util.Locale

/**
 * Ubicación del dispositivo para ubicacion (GeoJSON Point) en asistencias y cobros.
 * Usa la última posición conocida de LocationManager (sin Google Play Services).
 * En modo simulado, si no hay permiso o señal, devuelve un punto fijo dentro del mercado.
 */
object Gps {
    val PERMISOS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private val SIMULADA = GeoPoint(-98.20634, 19.04141, 6.0)
    private const val MAX_EDAD_MS = 10 * 60 * 1000L

    fun tienePermiso(ctx: Context) = PERMISOS.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    fun ultima(ctx: Context): GeoPoint? {
        if (!tienePermiso(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val mejor: Location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_EDAD_MS }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE } ?: return null
        return GeoPoint(mejor.longitude, mejor.latitude, if (mejor.hasAccuracy()) mejor.accuracy.toDouble() else null)
    }

    /** Posición para el registro; null si no hay señal (el cobro se marca para revisión). */
    fun posicion(ctx: Context, simulado: Boolean): GeoPoint? = ultima(ctx) ?: if (simulado) SIMULADA else null

    fun texto(p: GeoPoint?) = if (p == null) "Sin señal GPS" else String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon) + (p.precisionM?.let { String.format(Locale.US, " · ±%.0f m", it) } ?: "")
}
