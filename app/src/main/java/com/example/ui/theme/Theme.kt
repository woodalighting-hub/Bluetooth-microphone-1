package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonGreen,
    secondary = ElectricBlue,
    tertiary = CyberPink,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = WhiteAlpha90,
    onSurface = WhiteAlpha90
  )

private val LightColorScheme = DarkColorScheme // Always premium dark for a Spy Audio app!

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for the radar/spy vibe
  dynamicColor: Boolean = false, // Disable dynamic colors to keep the beautiful custom neon branding
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
