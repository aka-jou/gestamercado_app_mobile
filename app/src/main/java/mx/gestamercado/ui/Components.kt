package mx.gestamercado.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import mx.gestamercado.LocalApp
import mx.gestamercado.LocalNav
import mx.gestamercado.data.LocalStatus
import mx.gestamercado.data.Pago
import mx.gestamercado.util.fFecha
import mx.gestamercado.util.money

enum class Tone { DANGER, WARN, OK, INFO }

@Composable fun VSpace(h: Number) = Spacer(Modifier.height(h.toFloat().dp))
@Composable fun HSpace(w: Number) = Spacer(Modifier.width(w.toFloat().dp))

fun Modifier.borderBottom(width: Dp = 1.5.dp, color: Color = C.Ink) = this.drawBehind {
    val w = width.toPx()
    drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

fun Modifier.borderTop(width: Dp = 1.5.dp, color: Color = C.Ink) = this.drawBehind {
    val w = width.toPx()
    drawLine(color, Offset(0f, w / 2), Offset(size.width, w / 2), w)
}

fun statusBg(s: LocalStatus) = when (s) {
    LocalStatus.AL_CORRIENTE -> C.Teal
    LocalStatus.PENDIENTE -> C.Yellow
    LocalStatus.MOROSO -> C.Magenta
    LocalStatus.DESOCUPADO -> C.Gray
}

fun statusFg(s: LocalStatus) = if (s == LocalStatus.PENDIENTE || s == LocalStatus.DESOCUPADO) C.Ink else Color.White

fun statusName(s: LocalStatus) = when (s) {
    LocalStatus.AL_CORRIENTE -> "Al corriente"
    LocalStatus.PENDIENTE -> "Pendiente"
    LocalStatus.MOROSO -> "Moroso"
    LocalStatus.DESOCUPADO -> "Desocupado"
}

fun estadoTag(e: String): Pair<Color, Color> = when (e) {
    "PAGADO", "VIGENTE", "SINCRONIZADO" -> C.TealTint to C.TealDark
    "PARCIAL", "EN COLA" -> C.YellowTint to C.YellowText
    "CANCELADO", "REQUIERE REVISIÓN" -> C.Pink to C.Magenta
    else -> C.Sand to C.Body
}

@Composable
fun Mark(size: Dp = 30.dp, negative: Boolean = true) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 104f
        rotate(45f) {
            for (y in listOf(14f, 40f, 66f)) for (x in listOf(14f, 40f, 66f)) {
                val c = when {
                    x == 40f && y == 40f -> C.Magenta
                    x == 14f && y == 66f -> if (negative) Color.White else C.Ink
                    else -> C.Teal
                }
                drawRoundRect(c, topLeft = Offset((x + 2f) * s, (y + 2f) * s), size = Size(20f * s, 20f * s), cornerRadius = CornerRadius(2f * s))
            }
        }
    }
}

@Composable
fun Wordmark(size: Float = 24f, color: Color = Color.White, accent: Color? = null) {
    Column {
        Text("gesta", style = sans(size, FontWeight.Normal, color, -size * 0.04f, 0.95))
        Text("mercado", style = sans(size, FontWeight.ExtraBold, accent ?: color, -size * 0.04f, 0.95))
    }
}

@Composable
fun Diamond(size: Dp = 8.dp, color: Color, hollow: Boolean = false, stroke: Dp = 1.5.dp) {
    Box(Modifier.size(size * 1.42f), contentAlignment = Alignment.Center) {
        val m = Modifier.size(size).rotate(45f)
        Box(if (hollow) m.border(stroke, color) else m.background(color))
    }
}

/** Rombo decorativo de fondo (borde grueso al 14 %). */
@Composable
fun BoxScope.DecoDiamond(size: Dp, color: Color, alignment: Alignment, dx: Dp, dy: Dp) {
    Box(Modifier.align(alignment).offset(dx, dy).size(size).rotate(45f).border(size / 9, color.copy(alpha = 0.14f)))
}

@Composable
fun StatusChip(text: String, sub: String? = null, dot: Color, hollow: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.height(26.dp).border(1.dp, C.BarBorder).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Diamond(7.dp, dot, hollow)
        HSpace(6)
        Text(text, style = sans(10.5, FontWeight.Bold, Color.White, 0.8))
        if (sub != null) {
            HSpace(6)
            Text(sub, style = mono(10.5, color = C.BarSub))
        }
    }
}

@Composable
fun SquareBack(dark: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).border(1.5.dp, if (dark) C.BarBorder else C.Ink).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Atrás", tint = if (dark) Color.White else C.Ink)
    }
}

@Composable
fun Avatar(text: String, color: Color = C.Teal) {
    Box(Modifier.size(36.dp).background(color), contentAlignment = Alignment.Center) {
        Text(text, style = sans(12, FontWeight.Bold, Color.White))
    }
}

@Composable
fun BarAction(text: String, onClick: () -> Unit) {
    Box(Modifier.height(36.dp).border(1.5.dp, C.BarBorder).clickable(onClick = onClick).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
        Text(text, style = sans(12.5, FontWeight.Bold, Color.White))
    }
}

/** TopAppBar del módulo inspector con estado de red/sincronización y enlace ESC/POS. */
@Composable
fun InspectorBar(eyebrow: String, title: String, back: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    val app = LocalApp.current
    val nav = LocalNav.current
    Column(Modifier.fillMaxWidth().background(C.Ink).statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (back) SquareBack { nav.pop() } else Mark(30.dp)
            HSpace(12)
            Column(Modifier.weight(1f)) {
                Text(eyebrow, style = label(C.Muted, 10), maxLines = 1, overflow = TextOverflow.Ellipsis)
                VSpace(2)
                Text(title, style = sans(17, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        VSpace(10)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(if (app.online) "EN LÍNEA" else "OFFLINE", "${app.pendientes} por subir", if (app.online) C.Teal else C.Yellow) { app.online = !app.online }
            StatusChip("ESC/POS", if (app.printer) "XP-P323B" else "sin enlazar", if (app.printer) C.Teal else C.Muted, !app.printer) { app.printer = !app.printer }
        }
    }
}

@Composable
fun SimpleBar(eyebrow: String, title: String, back: Boolean = false, avatar: String? = null) {
    val nav = LocalNav.current
    Row(
        Modifier.fillMaxWidth().background(C.Ink).statusBarsPadding().padding(16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back) SquareBack { nav.pop() } else Mark(30.dp)
        HSpace(12)
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = label(C.Muted, 10), maxLines = 1, overflow = TextOverflow.Ellipsis)
            VSpace(2)
            Text(title, style = sans(17, FontWeight.Bold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (avatar != null) Avatar(avatar, C.Magenta)
    }
}

@Composable
fun Tag(text: String, bg: Color = C.Ink, fg: Color = Color.White, outline: Boolean = false) {
    Box(
        Modifier.height(22.dp).then(if (outline) Modifier.border(1.dp, C.Ink) else Modifier.background(bg)).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = sans(10, FontWeight.Bold, if (outline) C.Ink else fg, 0.6), maxLines = 1)
    }
}

@Composable
fun TagEstado(e: String) {
    val (b, f) = estadoTag(e)
    Tag(e, b, f)
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = sans(11, FontWeight.Bold, ls = 1.8))
        HSpace(10)
        Box(Modifier.weight(1f).height(2.dp).background(C.Ink))
        if (trailing != null) {
            HSpace(10)
            trailing()
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    color: Color = C.Magenta,
    amount: String? = null,
    arrow: Boolean = false,
    height: Dp = 54.dp,
) {
    val enabled = onClick != null
    val fg = if (enabled) Color.White else C.Muted2
    Row(
        modifier.fillMaxWidth().height(height).background(if (enabled) color else C.GrayTint)
            .clickable(enabled = enabled) { onClick?.invoke() }
            .padding(start = 20.dp, end = if (arrow) 8.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (arrow || amount != null) Arrangement.SpaceBetween else Arrangement.Center,
    ) {
        Text(text, style = sans(15, FontWeight.Bold, fg), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (amount != null) Text(amount, style = mono(17, color = fg))
        if (arrow) {
            Box(Modifier.size(38.dp).background(if (enabled) C.Ink else C.Gray), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, height: Dp = 48.dp, fill: Color = Color.Transparent) {
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = height).background(fill).border(1.5.dp, C.Ink)
            .clickable(enabled = onClick != null) { onClick?.invoke() }.padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, textAlign = TextAlign.Center, style = sans(13.5, FontWeight.Bold, if (onClick != null) C.Ink else C.Muted, lh = 1.2))
    }
}

@Composable
fun BoxField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    monoFont: Boolean = false,
    obscure: Boolean = false,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    caps: KeyboardCapitalization = KeyboardCapitalization.None,
    prefixText: String? = null,
    suffix: (@Composable () -> Unit)? = null,
    minLines: Int = 1,
    borderColor: Color = C.Ink,
    helper: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    maxLength: Int? = null,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = label(), modifier = Modifier.weight(1f))
            if (titleTrailing != null) titleTrailing()
        }
        VSpace(6)
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onValueChange(if (maxLength != null) v.take(maxLength) else v) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = if (monoFont) mono(15) else sans(15),
            placeholder = { if (hint != null) Text(hint, style = sans(14, color = C.BarSub)) },
            prefix = if (prefixText != null) { { Text(prefixText, style = mono(15, color = C.Muted2)) } } else null,
            trailingIcon = suffix,
            singleLine = minLines == 1,
            minLines = minLines,
            visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = if (obscure) KeyboardType.Password else keyboardType),
            shape = RectangleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.Magenta,
                unfocusedBorderColor = borderColor,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                cursorColor = C.Magenta,
            ),
        )
        if (helper != null) {
            VSpace(6)
            helper()
        }
    }
}

@Composable
fun Segmented(options: List<String>, value: Int, height: Dp = 44.dp, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().border(1.5.dp, C.Ink)) {
        options.forEachIndexed { i, o ->
            if (i > 0) Box(Modifier.width(1.dp).height(height).background(C.Ink))
            Box(
                Modifier.weight(1f).height(height).background(if (i == value) C.Ink else Color.White).clickable { onChange(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(o, textAlign = TextAlign.Center, style = sans(11.5, FontWeight.Bold, if (i == value) Color.White else C.Ink, 0.6), maxLines = 1)
            }
        }
    }
}

@Composable
fun SelectChip(text: String, selected: Boolean, dot: Color? = null, fg: Color = C.Ink, onClick: () -> Unit) {
    Row(
        Modifier.height(32.dp).background(if (selected) C.Ink else Color.White).border(1.5.dp, C.Ink).clickable(onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Diamond(7.dp, if (selected) Color.White else dot)
            HSpace(6)
        }
        Text(text, style = sans(11.5, FontWeight.Bold, if (selected) Color.White else fg), maxLines = 1)
    }
}

@Composable
fun BoxCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    padding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().background(color).border(1.5.dp, C.Ink)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun KV(k: String, v: String, monoV: Boolean = false, color: Color = C.Ink, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().then(if (last) Modifier else Modifier.borderBottom(1.dp, C.Line)).padding(vertical = 9.dp)) {
        Text(k, style = sans(12, color = C.Muted2))
        HSpace(14)
        Text(v, textAlign = TextAlign.End, style = if (monoV) mono(12.5, color = color) else sans(12.5, color = color, lh = 1.4), modifier = Modifier.weight(1f))
    }
}

@Composable
fun MetricCell(
    t: String,
    v: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    bg: Color = Color.White,
    tc: Color = C.Muted,
    vc: Color = C.Ink,
    vs: Number = 16,
) {
    Column(modifier.background(bg).padding(14.dp)) {
        Text(t, style = label(tc, 9.5))
        VSpace(5)
        Text(v, style = mono(vs, color = vc))
        if (sub != null) {
            VSpace(4)
            Text(sub, style = sans(11.5, color = C.Muted2, lh = 1.35))
        }
    }
}

/** Fila de métricas con alto homogéneo; usa Modifier.weight(1f).fillMaxHeight() en cada celda. */
@Composable
fun MetricRow(border: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).then(if (border) Modifier.border(1.5.dp, C.Ink) else Modifier),
        content = content,
    )
}

@Composable fun VLine() = Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))

@Composable
fun HeroAmount(title: String, amount: String, sub: String, color: Color = C.Magenta) {
    Box(Modifier.fillMaxWidth().background(color).clipToBounds().padding(18.dp)) {
        DecoDiamond(140.dp, Color.White, Alignment.BottomEnd, 60.dp, 70.dp)
        Column {
            Text(title, style = label(C.PinkText, 10.5))
            VSpace(8)
            Text(amount, style = mono(36, color = Color.White, lh = 1))
            VSpace(8)
            Text(sub, style = sans(12.5, color = C.PinkText, lh = 1.4))
        }
    }
}

@Composable
fun Notice(text: String, title: String? = null, tone: Tone = Tone.INFO) {
    val (bg, mk, tc) = when (tone) {
        Tone.DANGER -> Triple(C.Pink, C.Magenta, C.Magenta)
        Tone.WARN -> Triple(C.YellowTint, C.Yellow, C.YellowText)
        Tone.OK -> Triple(C.TealTint, C.Teal, C.TealDark)
        Tone.INFO -> Triple(C.Sand, C.Muted2, C.Ink)
    }
    Row(Modifier.fillMaxWidth().background(bg).padding(12.dp, 11.dp)) {
        Box(Modifier.padding(top = 2.dp)) { Diamond(9.dp, mk) }
        HSpace(9)
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = sans(12, FontWeight.Bold, tc, 0.4))
                VSpace(3)
            }
            Text(text, style = sans(12.5, if (title == null) FontWeight.SemiBold else FontWeight.Normal, if (title == null) tc else C.Body, lh = 1.45))
        }
    }
}

@Composable
fun DashedLine() {
    Canvas(Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(C.Ink, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    }
}

@Composable
fun BrandNav(items: List<String>, index: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).borderTop()) {
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            items.forEachIndexed { i, t ->
                val sel = i == index
                Column(
                    Modifier.weight(1f).fillMaxHeight().then(if (sel) Modifier.borderTop(3.dp, C.Magenta) else Modifier).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Diamond(10.dp, if (sel) C.Magenta else C.Muted2, hollow = !sel)
                    VSpace(4)
                    Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis, style = sans(11.5, if (sel) FontWeight.Bold else FontWeight.SemiBold, if (sel) C.Ink else C.Muted2))
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
fun ReciboTile(pago: Pago, showLocal: Boolean = false, onClick: () -> Unit) {
    val p = pago
    BoxCard(color = if (p.cancelado) C.Cancel else Color.White, padding = PaddingValues(13.dp, 12.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.folio, style = mono(12.5, FontWeight.Medium), modifier = Modifier.weight(1f))
            TagEstado(if (p.cancelado) "CANCELADO" else "VIGENTE")
        }
        VSpace(7)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (showLocal) "${p.localId} · ${fFecha(p.fecha)}" else fFecha(p.fecha), style = sans(12.5, color = C.Body), modifier = Modifier.weight(1f))
            Text(money(p.monto), style = mono(17, color = if (p.cancelado) C.Muted else C.Ink))
        }
        VSpace(8)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(p.formaPago, outline = true)
            Tag(p.canal, C.Sand, C.Body)
        }
        if (p.cancelado && p.motivo != null) {
            VSpace(7)
            Text("Motivo: ${p.motivo}", style = sans(11.5, FontWeight.SemiBold, C.Magenta))
        }
    }
}

@Composable
fun QrCode(data: String, size: Dp) {
    val matrix = remember(data) {
        QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    }
    Canvas(Modifier.size(size).background(Color.White)) {
        val n = matrix.width
        val cell = this.size.width / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix.get(x, y)) drawRect(C.Ink, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

@Composable
fun QrGlyph(color: Color = Color.White) {
    Box(Modifier.size(20.dp).border(2.5.dp, color).padding(4.dp)) {
        Box(Modifier.size(5.dp).background(color).align(Alignment.TopStart))
        Box(Modifier.size(5.dp).background(color).align(Alignment.BottomEnd))
    }
}

@Composable
fun Dot(size: Dp, color: Color) = Box(Modifier.size(size).background(color, CircleShape))
