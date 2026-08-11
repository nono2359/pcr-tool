package cn.wthee.pcrtool.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import cn.wthee.pcrtool.R
import cn.wthee.pcrtool.ui.MainActivity

/**
 * 默认暗色主题
 */
private val DarkColorPalette = darkColorScheme(
    primary = colorPrimary,
    onPrimary = colorWhite,
    primaryContainer = colorBlack,
    onPrimaryContainer = colorPrimary,
    secondary = colorPrimary,
    onSecondary = colorBlack,
    secondaryContainer = colorPrimary,
    onSecondaryContainer = colorBlack,
//    tertiary = colorPrimary,
//    onTertiary = colorBlack,
//    tertiaryContainer = colorBlack,
//    onTertiaryContainer = colorPrimary,
//    background = colorBlack,
//    onBackground = colorWhite,
//    surface = colorSurfaceDark,
//    onSurface = colorSurface,
//    surfaceVariant = colorSurfaceDark,
//    onSurfaceVariant = colorSurface,
//    outline = colorGrayDark,
)

/**
 * 默认亮色主题
 */
private val LightColorPalette = lightColorScheme(
    primary = colorPrimaryDark,
    onPrimary = colorWhite,
    primaryContainer = colorWhite,
    onPrimaryContainer = colorPrimaryDark,
    secondary = colorPrimaryDark,
    onSecondary = colorWhite,
    secondaryContainer = colorPrimaryDark,
    onSecondaryContainer = colorWhite,
//    tertiary = colorPrimaryDark,
//    onTertiary = colorWhite,
//    tertiaryContainer = colorWhite,
//    onTertiaryContainer = colorPrimaryDark,
//    background = colorWhite,
//    onBackground = colorBlack,
//    surface = colorSurface,
//    onSurface = colorSurfaceDark,
//    surfaceVariant = colorSurface,
//    onSurfaceVariant = colorSurfaceDark,
//    outline = colorGray,
)

private val GenEiLateGoFontFamily = FontFamily(
    Font(R.font.genei_latego_p_v2)
)

private fun Typography.withFontFamily(fontFamily: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)

@SuppressLint("NewApi")
@Composable
fun PCRToolComposeTheme(
    shapes: Shapes = MaterialTheme.shapes,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {

    val baseTypography = MaterialTheme.typography
    val typography = baseTypography.withFontFamily(GenEiLateGoFontFamily)

    //启用动态色彩判断
    val dynamicColor =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MainActivity.dynamicColorOnFlag

    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColorPalette
        else -> LightColorPalette
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
        shapes = shapes
    )
}

/**
 * 预览正常、深色模式
 */
//@Preview(
//    name = "dark theme",
//    group = "dark",
//    uiMode = UI_MODE_NIGHT_YES,
//    showBackground = true
//)
@Preview(
    name = "normal",
    group = "light",
    showBackground = true
)
//@Preview(
//    name = "tablet",
//    group = "tablet",
//    device = Devices.TABLET,
//    showBackground = true
//)
annotation class CombinedPreviews

/**
 * 预览
 */
@Composable
fun PreviewLayout(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    themeType: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    val dynamicColor =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MainActivity.dynamicColorOnFlag

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .padding(Dimen.mediumPadding)
            .fillMaxWidth()
    ) {
        //正常主题
//        if (themeType == 0 || themeType == 1) {
//            MaterialTheme(if (isSystemInDarkTheme()) DarkColorPalette else LightColorPalette) {
//                Column(horizontalAlignment = horizontalAlignment) {
//                    content()
//                }
//            }
//        }
//
//        Divider(
//            modifier = Modifier
//                .padding(vertical = Dimen.mediumPadding)
//                .height(Dimen.divLineHeight)
//        )

        //动态色彩主题
        if (themeType == 0 || themeType == 2) {
            MaterialTheme(
                if (dynamicColor && isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(LocalContext.current)
                } else if (dynamicColor) {
                    dynamicLightColorScheme(LocalContext.current)
                } else {
                    LightColorPalette
                }
            ) {
                Column(horizontalAlignment = horizontalAlignment) {
                    content()
                }
            }
        }
    }

}
