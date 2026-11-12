package net.meshstar.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val MeshShape = RoundedCornerShape(3.dp)
val LocalMeshDark = staticCompositionLocalOf { false }
@Composable fun MeshTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val primary = if (dark) Color(0xFF32D74B) else Color(0xFF008000)
    val bg = if (dark) Color.Black else Color.White
    val colors = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary, onPrimary = bg, background = bg, surface = bg, onSurface = primary,
        onBackground = primary, secondary = primary.copy(alpha = .8f), onSurfaceVariant = primary.copy(alpha = .8f),
        outline = primary.copy(alpha = .25f), outlineVariant = primary.copy(alpha = .15f),
        error = Color(0xFFBF1A1A), surfaceVariant = primary.copy(alpha = .06f))
    fun style(size: Int) = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = size.sp)
    MaterialTheme(colorScheme = colors, typography = Typography(bodyLarge = style(14), bodyMedium = style(12), bodySmall = style(10),
        labelLarge = style(12), titleLarge = style(14), titleMedium = style(14), titleSmall = style(12))) {
        CompositionLocalProvider(LocalMeshDark provides dark, LocalTextStyle provides style(14), LocalContentColor provides primary, content = content)
    }
}
/** The painted rectangle and the non-overlapping touch area are separate. */
@Composable fun MeshButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { disabledIf(!enabled) }, contentAlignment = Alignment.CenterStart) {
        Text(text, Modifier.border(.5.dp, MaterialTheme.colorScheme.outline, MeshShape).padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .45f), fontSize = 12.sp)
    }
}
private fun SemanticsPropertyReceiver.disabledIf(value: Boolean) { if (value) disabled() }
@Composable fun MeshIconButton(label: String, icon: ImageVector, modifier: Modifier = Modifier, composer: Boolean = false,
                              enabled: Boolean = true, paintOffset: Int = 0, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clickable(enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; disabledIf(!enabled) }, contentAlignment = Alignment.Center) {
        Box(Modifier.offset(x = paintOffset.dp).size(if (composer) 22.dp else 26.dp).border(.5.dp, MaterialTheme.colorScheme.outline, MeshShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(if (composer) 15.dp else 16.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .4f))
        }
    }
}
@Composable fun MeshCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().border(.5.dp, MaterialTheme.colorScheme.outline, MeshShape).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}
@Composable fun MeshBadge(count: Int, modifier: Modifier = Modifier) {
    if (count > 0) Text(if (count > 99) "99+" else count.toString(),
        modifier.background(MaterialTheme.colorScheme.error, MeshShape).padding(horizontal = 3.dp, vertical = 1.dp),
        color = Color.White, fontSize = 9.sp, maxLines = 1)
}
@Composable fun Secondary(text: String, modifier: Modifier = Modifier, size: Int = 12, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, fontSize = size.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
fun peerColor(seed: String, dark: Boolean): Color {
    var hash = 5381L
    seed.toByteArray(Charsets.UTF_8).forEach { hash = (hash * 33 + (it.toInt() and 255)) and 0xffffffffL }
    var hue = (hash % 360).toFloat()
    if (hue in 15f..45f) hue += 45f
    return Color.hsv(hue, if (dark) .75f else .8f, if (dark) .75f else .45f)
}
