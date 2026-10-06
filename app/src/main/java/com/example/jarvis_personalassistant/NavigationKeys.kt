package com.example.jarvis_personalassistant

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable data object GestureMappings : NavKey
@Serializable data object CommandsReference : NavKey
