package ke.mpesa.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Forest = Color(0xFF12503C)
private val Mint = Color(0xFF7BE0A8)
private val Clay = Color(0xFFB4462E)
private val Sand = Color(0xFFF7F4EE)
private val Ink = Color(0xFF15211C)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFEBDC),
    onPrimaryContainer = Color(0xFF06291D),
    secondary = Color(0xFF4C6659),
    tertiary = Clay,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E3DA),
    onSurfaceVariant = Color(0xFF4A4A44),
    error = Clay
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF06291D),
    primaryContainer = Color(0xFF1E5741),
    onPrimaryContainer = Color(0xFFCFEBDC),
    secondary = Color(0xFFB2CCBC),
    tertiary = Color(0xFFFFB4A0),
    background = Color(0xFF11150F),
    onBackground = Color(0xFFE3E3DC),
    surface = Color(0xFF1A1F1B),
    onSurface = Color(0xFFE3E3DC),
    surfaceVariant = Color(0xFF3F4A43),
    onSurfaceVariant = Color(0xFFC0CBC2)
)

@Composable
fun PesaTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

// ---- formatting ----

fun money(value: Double): String = "Ksh " + "%,.2f".format(value)

fun moneyShort(value: Double): String =
    if (value % 1.0 == 0.0) "Ksh " + "%,.0f".format(value) else money(value)

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")

fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFmt)

fun formatDay(date: LocalDate): String = when (date) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> date.format(dayFmt)
}

fun formatMonth(date: LocalDate): String = date.format(monthFmt)

fun ordinal(day: Int): String {
    val suffix = when {
        day in 11..13 -> "th"
        day % 10 == 1 -> "st"
        day % 10 == 2 -> "nd"
        day % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}
