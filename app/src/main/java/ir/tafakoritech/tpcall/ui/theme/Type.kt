package ir.tafakoritech.tpcall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.tafakoritech.tpcall.R

/**
 * Vazirmatn — an open-source (OFL) Persian-optimized typeface. Bundled locally.
 */
val VazirmatnFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val Persian = VazirmatnFontFamily

/**
 * Typography hierarchy tuned for Persian rendering. Letter-spacing is kept at
 * zero because Persian script does not benefit from Latin-style tracking.
 */
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = Persian, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 48.sp),
    displayMedium = TextStyle(fontFamily = Persian, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 44.sp),
    displaySmall = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp),

    headlineLarge = TextStyle(fontFamily = Persian, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Persian, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Persian, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),

    titleLarge = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),

    bodyLarge = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),

    labelLarge = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = Persian, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
)
