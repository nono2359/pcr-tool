package cn.wthee.pcrtool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

val LocalDynamicColorEnabled = staticCompositionLocalOf { false }

@Composable
fun adaptiveForegroundColor(color: Color, minimumContrast: Float = 4.5f): Color {
    if (
        !LocalDynamicColorEnabled.current ||
        color == Color.Unspecified ||
        color == Color.Black ||
        color == Color.White
    ) return color

    val scheme = MaterialTheme.colorScheme
    val backgrounds = listOf(scheme.background, scheme.surface, scheme.surfaceVariant)
    if (backgrounds.all { contrastRatio(color, it) >= minimumContrast }) return color

    val towardBlack = adjustedColor(color, Color.Black, backgrounds, minimumContrast)
    val towardWhite = adjustedColor(color, Color.White, backgrounds, minimumContrast)
    return if (towardBlack.second <= towardWhite.second) towardBlack.first else towardWhite.first
}

@Composable
fun adaptiveContentColor(
    backgroundColor: Color,
    preferredColor: Color = Color.White,
    minimumContrast: Float = 4.5f,
): Color {
    if (!LocalDynamicColorEnabled.current || backgroundColor == Color.Unspecified) {
        return preferredColor
    }
    if (contrastRatio(preferredColor, backgroundColor) >= minimumContrast) {
        return preferredColor
    }

    val blackContrast = contrastRatio(Color.Black, backgroundColor)
    val whiteContrast = contrastRatio(Color.White, backgroundColor)
    val fallback = if (blackContrast >= whiteContrast) Color.Black else Color.White
    return fallback.copy(alpha = preferredColor.alpha)
}
private fun adjustedColor(
    source: Color,
    target: Color,
    backgrounds: List<Color>,
    minimumContrast: Float
): Pair<Color, Float> {
    if (backgrounds.any { contrastRatio(target, it) < minimumContrast }) {
        return target.copy(alpha = source.alpha) to Float.POSITIVE_INFINITY
    }

    var low = 0f
    var high = 1f
    repeat(12) {
        val amount = (low + high) / 2f
        val candidate = lerp(source, target, amount).copy(alpha = source.alpha)
        if (backgrounds.all { contrastRatio(candidate, it) >= minimumContrast }) {
            high = amount
        } else {
            low = amount
        }
    }
    return lerp(source, target, high).copy(alpha = source.alpha) to high
}

private fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (min(firstLuminance, secondLuminance) + 0.05f)
}