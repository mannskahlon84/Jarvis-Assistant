package com.example.jarvis_personalassistant.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun JARVISPersonalAssistantTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = darkColorScheme(
    primary = JarvisPrimary,
    secondary = JarvisSecondary,
    tertiary = JarvisAccent,
    background = JarvisBackground,
    surface = JarvisCardBg,
    onPrimary = JarvisBackground,
    onSecondary = JarvisBackground,
    onTertiary = JarvisBackground,
    onBackground = JarvisTextPrimary,
    onSurface = JarvisTextPrimary,
    error = JarvisError
  )

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
