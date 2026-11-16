package pl.septrener.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF00579B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E4FF), onPrimaryContainer = Color(0xFF001C38),
    secondary = Color(0xFF8A5100), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCBE), onSecondaryContainer = Color(0xFF2C1600),
    tertiaryContainer = Color(0xFFFFF0C2),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA0CAFF), onPrimary = Color(0xFF003259),
    primaryContainer = Color(0xFF00487F), onPrimaryContainer = Color(0xFFD2E4FF),
    secondary = Color(0xFFFFB870), onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF693C00), onSecondaryContainer = Color(0xFFFFDCBE),
    tertiaryContainer = Color(0xFF4D3F00),
)

private val base = Typography()
private val AppTypography = base.copy(
    bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
    titleMedium = base.titleMedium.copy(fontSize = 18.sp),
    labelLarge = base.labelLarge.copy(fontSize = 16.sp),
)

@Composable
fun SepTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
