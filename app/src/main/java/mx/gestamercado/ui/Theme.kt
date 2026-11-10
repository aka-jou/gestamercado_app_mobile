@file:OptIn(ExperimentalTextApi::class)

package mx.gestamercado.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import mx.gestamercado.R

object C {
    val Ink = Color(0xFF121517)
    val Magenta = Color(0xFFC80766)
    val Pink = Color(0xFFFCE7EF)
    val PinkText = Color(0xFFFFE3EE)
    val Teal = Color(0xFF1C9988)
    val TealDark = Color(0xFF0F6B60)
    val TealTint = Color(0xFFE1F2EF)
    val TealLight = Color(0xFF7FD4C6)
    val Bg = Color(0xFFFFFDFB)
    val Sand = Color(0xFFF4F1EC)
    val Line = Color(0xFFEEEAE3)
    val Muted = Color(0xFF8E9B98)
    val Muted2 = Color(0xFF6E7B78)
    val Body = Color(0xFF43514E)
    val Yellow = Color(0xFFE3A72F)
    val YellowLight = Color(0xFFF2C766)
    val YellowTint = Color(0xFFFFF8DD)
    val YellowText = Color(0xFF7A5A00)
    val Gray = Color(0xFFC9CFCD)
    val GrayTint = Color(0xFFE4E9E7)
    val BarBorder = Color(0xFF3A4245)
    val BarSub = Color(0xFFA7B3B0)
    val Cancel = Color(0xFFFBF9F6)
}

/** Archivo es una fuente variable: cada peso fija el eje wght automáticamente (API 26+). */
val Archivo = FontFamily(
    Font(R.font.archivo, FontWeight.Normal),
    Font(R.font.archivo, FontWeight.Medium),
    Font(R.font.archivo, FontWeight.SemiBold),
    Font(R.font.archivo, FontWeight.Bold),
    Font(R.font.archivo, FontWeight.ExtraBold),
)

val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

private fun style(family: FontFamily, size: Number, w: FontWeight, color: Color, ls: Number, lh: Number?) = TextStyle(
    fontFamily = family,
    fontSize = size.toFloat().sp,
    fontWeight = w,
    color = color,
    letterSpacing = ls.toFloat().sp,
    lineHeight = if (lh != null) (size.toFloat() * lh.toFloat()).sp else TextUnit.Unspecified,
)

fun sans(size: Number, w: FontWeight = FontWeight.Normal, color: Color = C.Ink, ls: Number = 0, lh: Number? = null) = style(Archivo, size, w, color, ls, lh)
fun mono(size: Number, w: FontWeight = FontWeight.Normal, color: Color = C.Ink, ls: Number = 0, lh: Number? = null) = style(DmMono, size, w, color, ls, lh)
fun label(color: Color = C.Muted, size: Number = 10.5) = sans(size, FontWeight.Bold, color, 1.3)

@Composable
fun GestaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = C.Ink, secondary = C.Magenta, background = C.Bg, surface = C.Bg, onPrimary = Color.White),
        content = content,
    )
}
