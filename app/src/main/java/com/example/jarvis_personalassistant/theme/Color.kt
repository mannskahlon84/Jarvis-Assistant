package com.example.jarvis_personalassistant.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object JarvisThemeConfig {
    var background by mutableStateOf(Color(0xFFF8FAFC)) // Default: White Background
    var cardBg by mutableStateOf(Color(0xFFFFFFFF))      // Default: White Cards
    var primary by mutableStateOf(Color(0xFF0082FF))     // Default Blue Accent
    var secondary by mutableStateOf(Color(0xFF0052D4))
    var accent by mutableStateOf(Color(0xFF4CAF50))
    var textPrimary by mutableStateOf(Color(0xFF0F172A))  // Default Dark slate text
    var textSecondary by mutableStateOf(Color(0xFF475569))
    var terminalText by mutableStateOf(Color(0xFF020617))
    var error by mutableStateOf(Color(0xFFFF1744))

    fun updateTheme(themeMode: String, fontColorPreset: String) {
        val isDark = themeMode.uppercase() == "DARK"
        
        // Background and Card colors
        if (isDark) {
            background = Color(0xFF030712)   // Space black
            cardBg = Color(0xFF0F172A)       // Navy slate card
            textPrimary = Color(0xFFF1F5F9)  // Off white text
            textSecondary = Color(0xFF94A3B8)// Muted slate text
            terminalText = Color(0xFF00FFCC) // Neon green/cyan for logs
        } else {
            background = Color(0xFFF8FAFC)   // Sleek off white
            cardBg = Color(0xFFFFFFFF)       // Pure white card
            textPrimary = Color(0xFF0F172A)  // Dark slate text
            textSecondary = Color(0xFF475569)// Slate text
            terminalText = Color(0xFF020617) // Dark text for logs
        }

        // Accent / Font colors
        when (fontColorPreset.uppercase()) {
            "CYAN" -> {
                primary = Color(0xFF00E5FF)
                secondary = Color(0xFF0082FF)
                accent = Color(0xFF76FF03)
            }
            "BLUE" -> {
                primary = Color(0xFF0082FF)
                secondary = Color(0xFF0052D4)
                accent = Color(0xFF4CAF50)
            }
            "GREEN" -> {
                primary = Color(0xFF00E676)
                secondary = Color(0xFF00C853)
                accent = Color(0xFF76FF03)
            }
            "RED" -> {
                primary = Color(0xFFFF1744)
                secondary = Color(0xFFD50000)
                accent = Color(0xFF00E5FF)
            }
            "YELLOW" -> {
                primary = Color(0xFFFFEA00)
                secondary = Color(0xFFFFD600)
                accent = Color(0xFF00E5FF)
            }
            "MAGENTA" -> {
                primary = Color(0xFFFF007F)
                secondary = Color(0xFFD500F9)
                accent = Color(0xFF00E5FF)
            }
            "PURPLE" -> {
                primary = Color(0xFFD500F9)
                secondary = Color(0xFF651FFF)
                accent = Color(0xFF76FF03)
            }
            else -> { // Default Blue
                primary = Color(0xFF0082FF)
                secondary = Color(0xFF0052D4)
                accent = Color(0xFF4CAF50)
            }
        }
    }
}

val JarvisBackground get() = JarvisThemeConfig.background
val JarvisCardBg get() = JarvisThemeConfig.cardBg
val JarvisPrimary get() = JarvisThemeConfig.primary
val JarvisSecondary get() = JarvisThemeConfig.secondary
val JarvisAccent get() = JarvisThemeConfig.accent
val JarvisTextPrimary get() = JarvisThemeConfig.textPrimary
val JarvisTextSecondary get() = JarvisThemeConfig.textSecondary
val JarvisTerminalText get() = JarvisThemeConfig.terminalText
val JarvisError get() = JarvisThemeConfig.error
