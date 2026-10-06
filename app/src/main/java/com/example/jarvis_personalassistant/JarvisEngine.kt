package com.example.jarvis_personalassistant

import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.graphics.*
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

object JarvisEngine {
    private const val TAG = "JarvisEngine"
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _logs = MutableStateFlow<List<String>>(listOf("System initialized."))
    val logs = _logs.asStateFlow()

    private val _speechStatus = MutableStateFlow("Idle")
    val speechStatus = _speechStatus.asStateFlow()

    private val _faceExpression = MutableStateFlow("None")
    val faceExpression = _faceExpression.asStateFlow()

    private val _isFaceTrackingActive = MutableStateFlow(false)
    val isFaceTrackingActive = _isFaceTrackingActive.asStateFlow()

    private val _userName = MutableStateFlow("Sir")
    val userName = _userName.asStateFlow()

    private val _assistantName = MutableStateFlow("Siri")
    val assistantName = _assistantName.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage = _selectedLanguage.asStateFlow()

    private val _workOnLockScreen = MutableStateFlow(false)
    val workOnLockScreen = _workOnLockScreen.asStateFlow()

    private val _latestVoiceText = MutableStateFlow("")
    val latestVoiceText = _latestVoiceText.asStateFlow()

    private val _themeMode = MutableStateFlow("WHITE")
    val themeMode = _themeMode.asStateFlow()

    private val _fontColorPreset = MutableStateFlow("BLUE")
    val fontColorPreset = _fontColorPreset.asStateFlow()

    private val _iconColorPreset = MutableStateFlow("BLUE")
    val iconColorPreset = _iconColorPreset.asStateFlow()

    private val _ambientLightColor = MutableStateFlow("#00FFFF")
    val ambientLightColor = _ambientLightColor.asStateFlow()

    private val _isConversing = MutableStateFlow(true)
    val isConversing = _isConversing.asStateFlow()

    private val _voicePitchTarget = MutableStateFlow(0f)
    val voicePitchTarget = _voicePitchTarget.asStateFlow()

    fun setVoicePitchTarget(pitch: Float) {
        _voicePitchTarget.value = pitch
    }

    private val _voiceEmbeddingTarget = MutableStateFlow<FloatArray?>(null)
    val voiceEmbeddingTarget = _voiceEmbeddingTarget.asStateFlow()

    fun setVoiceEmbeddingTarget(context: Context, embedding: FloatArray) {
        _voiceEmbeddingTarget.value = embedding
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("voice_embedding_target", embedding.joinToString(",")).apply()
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        _userName.value = prefs.getString("user_name", "Sir") ?: "Sir"
        var savedName = prefs.getString("assistant_name", "Siri") ?: "Siri"
        if (savedName == "Jarvis") {
            savedName = "Siri"
            prefs.edit().putString("assistant_name", "Siri").apply()
        }
        _assistantName.value = savedName
        _selectedLanguage.value = prefs.getString("selected_language", "en") ?: "en"
        _workOnLockScreen.value = prefs.getBoolean("work_on_lock_screen", false)
        _isFaceTrackingActive.value = prefs.getBoolean("face_tracking_active", false)
        
        val tMode = prefs.getString("theme_mode", "WHITE") ?: "WHITE"
        val fColor = prefs.getString("font_color", "BLUE") ?: "BLUE"
        val iColor = prefs.getString("icon_color", "BLUE") ?: "BLUE"
        val aColor = prefs.getString("ambient_light_color", "#00FFFF") ?: "#00FFFF"
        _themeMode.value = tMode
        _fontColorPreset.value = fColor
        _iconColorPreset.value = iColor
        _ambientLightColor.value = aColor
        _voicePitchTarget.value = prefs.getFloat("voice_pitch_target", 0f)
        val embeddingStr = prefs.getString("voice_embedding_target", null)
        if (!embeddingStr.isNullOrEmpty()) {
            try {
                _voiceEmbeddingTarget.value = embeddingStr.split(",").map { it.toFloat() }.toFloatArray()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse target voice embedding", e)
            }
        }
        
        com.example.jarvis_personalassistant.theme.JarvisThemeConfig.updateTheme(tMode, fColor)
        initTts(context)
    }

    fun getLocale(langCode: String): Locale {
        return when (langCode) {
            "en" -> Locale.US
            "ar" -> Locale("ar")
            "de" -> Locale.GERMANY
            "es" -> Locale("es", "ES")
            "hi" -> Locale("hi", "IN")
            "pa" -> Locale("pa", "IN")
            "ur" -> Locale("ur", "PK")
            "ml" -> Locale("ml", "IN")
            "ta" -> Locale("ta", "IN")
            "bn" -> Locale("bn", "BD")
            "ne" -> Locale("ne", "NP")
            else -> Locale.US
        }
    }

    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking = _isTtsSpeaking.asStateFlow()

    fun isSpeaking(): Boolean = _isTtsSpeaking.value

    fun stopSpeaking() {
        if (isTtsReady) {
            tts?.stop()
            _isTtsSpeaking.value = false
            log("System: TTS stopped by barge-in.")
        }
    }

    fun initTts(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = getLocale(_selectedLanguage.value)
                tts?.language = locale
                
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isTtsSpeaking.value = true
                        JarvisSpeechListener.pauseTemporarily()
                    }

                    override fun onDone(utteranceId: String?) {
                        _isTtsSpeaking.value = false
                        JarvisSpeechListener.resumeTemporarily()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isTtsSpeaking.value = false
                        JarvisSpeechListener.resumeTemporarily()
                    }
                })

                isTtsReady = true
                speak("${_assistantName.value} is online, ${_userName.value}.")
                log("${_assistantName.value}: Online.")
                resetInactivityTimer(context)
            } else {
                log("System: Failed to initialize TTS.")
            }
        }
    }

    fun updateSettings(context: Context, newUserName: String, newAssistantName: String, langCode: String) {
        _userName.value = newUserName
        _assistantName.value = newAssistantName
        val langChanged = _selectedLanguage.value != langCode
        _selectedLanguage.value = langCode

        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("user_name", newUserName)
            putString("assistant_name", newAssistantName)
            putString("selected_language", langCode)
        }.apply()

        log("System: Settings updated. Language: $langCode. User: $newUserName, Assistant: $newAssistantName")

        if (tts != null) {
            val locale = getLocale(langCode)
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                log("System: TTS language $langCode not supported or missing data on this device.")
            } else {
                if (langChanged) {
                    log("System: TTS language switched to $langCode")
                    speak("Language updated, $newUserName.")
                }
            }
        }
    }

    fun updateLockScreenSetting(context: Context, active: Boolean) {
        _workOnLockScreen.value = active
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("work_on_lock_screen", active).apply()
        log("System: Work on Lock Screen is now ${if (active) "ENABLED" else "DISABLED"}.")
    }

    private var inactivityJob: kotlinx.coroutines.Job? = null

    fun resetInactivityTimer(context: Context) {
        inactivityJob?.cancel()
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val timeout = prefs.getLong("inactivity_timeout_ms", 40000L)
        if (timeout <= 0L) return // Disables inactivity timeout

        inactivityJob = scope.launch {
            kotlinx.coroutines.delay(timeout)
            if (_isConversing.value) {
                _isConversing.value = false
                speak(getSleepGreeting())
                log("System: Sleep mode activated due to ${timeout / 1000}s inactivity.")
            }
        }
    }

    fun updateVoiceAccessSettings(
        requireVerbs: Boolean,
        inactivityTimeoutMs: Long,
        incomingCallWake: Boolean,
        activeDuringCalls: Boolean,
        recognizeCommonIcons: Boolean,
        activationKeyEnabled: Boolean
    ) {
        log("System: Voice Access settings synchronized (Require Verbs: $requireVerbs, Inactivity Timeout: ${inactivityTimeoutMs / 1000}s, Incoming Call Wake: $incomingCallWake, Active During Calls: $activeDuringCalls, Recognize Common Icons: $recognizeCommonIcons, Activation Key Enabled: $activationKeyEnabled)")
    }

    fun getWakeGreeting(): String {
        return when (_selectedLanguage.value) {
            "en" -> "How can I help you?"
            "ar" -> "كيف يمكنني مساعدتك؟"
            "de" -> "Wie kann ich Ihnen helfen?"
            "es" -> "¿Cómo puedo ayudarte?"
            "hi" -> "मैं आपकी क्या मदद कर सकता हूँ?"
            "pa" -> "ਮੈਂ ਤੁਹਾਡੀ ਕੀ ਮਦਦ ਕਰ ਸਕਦਾ ਹਾਂ?"
            "ur" -> "میں آپ کی کیا مدد کر سکتا ہوں؟"
            "ml" -> "ഞാൻ എങ്ങനെയാണ് സഹായിക്കേണ്ടത്?"
            "ta" -> "நான் உங்களுக்கு எவ்வாறு உதவ முடியும்?"
            "bn" -> "আমি আপনাকে কিভাবে সাহায্য করতে পারি?"
            "ne" -> "म तपाईंलाई कसरी मद्दत गर्न सक्छु?"
            else -> "How can I help you?"
        }
    }

    fun getSleepGreeting(): String {
        return when (_selectedLanguage.value) {
            "en" -> "Going to sleep now."
            "ar" -> "سأذهب للنوم الآن."
            "de" -> "Ich gehe jetzt schlafen."
            "es" -> "Me voy a dormir ahora."
            "hi" -> "अब मैं सोने जा रहा हूँ।"
            "pa" -> "ਹੁਣ ਮੈਂ ਸੌਣ ਜਾ ਰਿਹਾ ਹਾਂ।"
            "ur" -> "اب میں سونے جا رہا ہوں۔"
            "ml" -> "ഞാൻ ഇപ്പോൾ ഉറങ്ങാൻ പോകുന്നു."
            "ta" -> "நான் இப்போது தூங்கப் போகிறேன்."
            "bn" -> "আমি এখন ঘুমাতে যাচ্ছি।"
            "ne" -> "म अब सुत्न जाँदैछु।"
            else -> "Going to sleep now."
        }
    }

    fun updateThemeSettings(context: Context, themeMode: String, fontColorPreset: String, iconColorPreset: String) {
        _themeMode.value = themeMode
        _fontColorPreset.value = fontColorPreset
        _iconColorPreset.value = iconColorPreset

        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("theme_mode", themeMode)
            putString("font_color", fontColorPreset)
            putString("icon_color", iconColorPreset)
        }.apply()

        com.example.jarvis_personalassistant.theme.JarvisThemeConfig.updateTheme(themeMode, fontColorPreset)
        log("System: Theme updated to $themeMode. Font Accent: $fontColorPreset. App Icon: $iconColorPreset")

        updateLauncherIcon(context, iconColorPreset)
    }

    fun updateAmbientLightColor(context: Context, hexColor: String) {
        _ambientLightColor.value = hexColor
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("ambient_light_color", hexColor).apply()
        log("System: Ambient light color updated to $hexColor")
    }

    private fun updateLauncherIcon(context: Context, colorName: String) {
        val colors = listOf("BLUE", "GREEN", "RED", "YELLOW", "MAGENTA")
        val cleanColor = colorName.uppercase().trim()
        if (cleanColor !in colors) return

        val packageManager = context.packageManager
        val packageName = context.packageName

        for (c in colors) {
            val aliasSuffix = when (c) {
                "BLUE" -> "LauncherBlue"
                "GREEN" -> "LauncherGreen"
                "RED" -> "LauncherRed"
                "YELLOW" -> "LauncherYellow"
                "MAGENTA" -> "LauncherMagenta"
                else -> "LauncherBlue"
            }
            val component = android.content.ComponentName(packageName, "$packageName.$aliasSuffix")
            val isTarget = c == cleanColor
            val newState = if (isTarget) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            try {
                val currentState = packageManager.getComponentEnabledSetting(component)
                if (currentState != newState) {
                    packageManager.setComponentEnabledSetting(
                        component,
                        newState,
                        android.content.pm.PackageManager.DONT_KILL_APP
                    )
                    log("System: Component $aliasSuffix state changed to $newState")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating enabled setting for launcher alias $aliasSuffix", e)
            }
        }
    }

    fun log(message: String) {
        val current = _logs.value.toMutableList()
        current.add("[${System.currentTimeMillis() % 100000}] $message")
        if (current.size > 50) current.removeAt(0)
        _logs.value = current
        Log.d(TAG, message)
    }

    fun speak(text: String) {
        if (isTtsReady) {
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "jarvis_speak")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "jarvis_speak")
        }
    }

    fun updateSpeechStatus(status: String) {
        _speechStatus.value = status
    }

    fun executeAction(context: Context, actionType: String, actionValue: String) {
        val type = actionType.uppercase().trim()
        log("System: Executing Action - $type ($actionValue)")
        when (type) {
            "BACK" -> {
                JarvisAccessibilityService.performBack()
            }
            "HOME" -> {
                JarvisAccessibilityService.performHome()
            }
            "OPEN_APP" -> {
                openApp(context, actionValue)
            }
            "TYPE_TEXT" -> {
                typeText(actionValue)
            }
        }
    }

    fun updateFaceTrackingActive(context: Context, active: Boolean) {
        _isFaceTrackingActive.value = active
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("face_tracking_active", active).apply()
        log("System: Face Tracking is now ${if (active) "ONLINE" else "OFFLINE"}.")
    }

    fun updateFaceExpression(expression: String) {
        if (expression != "None" && expression.isNotBlank()) {
            _faceExpression.value = expression
            log("Face gesture: $expression detected.")
            val context = appContext ?: return
            handleFaceExpression(context, expression)
            scope.launch {
                kotlinx.coroutines.delay(1500)
                if (_faceExpression.value == expression) {
                    _faceExpression.value = "None"
                }
            }
        }
    }

    private fun handleFaceExpression(context: Context, expression: String) {
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val (actionKey, defaultAction) = when (expression) {
            "Smiling" -> Pair("gesture_smile_action", "NONE")
            "Winking" -> Pair("gesture_wink_action", "BACK")
            "Blinking" -> Pair("gesture_blink_action", "NONE")
            "Head Turn Left" -> Pair("gesture_turn_left_action", "NONE")
            "Head Turn Right" -> Pair("gesture_turn_right_action", "NONE")
            "Head Tilt Left" -> Pair("gesture_tilt_left_action", "NONE")
            "Head Tilt Right" -> Pair("gesture_tilt_right_action", "NONE")
            else -> return
        }
        val valueKey = when (expression) {
            "Smiling" -> "gesture_smile_value"
            "Winking" -> "gesture_wink_value"
            "Blinking" -> "gesture_blink_value"
            "Head Turn Left" -> "gesture_turn_left_value"
            "Head Turn Right" -> "gesture_turn_right_value"
            "Head Tilt Left" -> "gesture_tilt_left_value"
            "Head Tilt Right" -> "gesture_tilt_right_value"
            else -> return
        }
        val actionType = prefs.getString(actionKey, defaultAction) ?: defaultAction
        val actionValue = prefs.getString(valueKey, "") ?: ""
        
        if (actionType != "NONE") {
            speak("Gesture $expression recognized.")
            executeAction(context, actionType, actionValue)
        }
    }

    private fun checkCustomVoiceCommand(context: Context, command: String): Boolean {
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_voice_commands", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(jsonStr)
            val clean = command.lowercase().trim()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val trigger = obj.getString("trigger").lowercase().trim()
                if (clean == trigger) {
                    val action = obj.getString("action")
                    val value = obj.getString("value")
                    speak("Executing custom command.")
                    executeAction(context, action, value)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking custom voice commands", e)
        }
        return false
    }

    fun processCommand(context: Context, command: String) {
        _latestVoiceText.value = command
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        log("User: \"$command\"")

        val assistantNameLower = assistantName.value.lowercase()
        var cleanCommand = command.lowercase().trim()
            .replace(",", "")
            .replace(".", "")
            .replace("?", "")
            .replace("!", "")
            .trim()

        // Strip wake prefixes (like "siri ", "hey siri ", "ok siri ", or ending in " siri")
        val prefixesToStrip = listOf(
            "hey $assistantNameLower ",
            "ok $assistantNameLower ",
            "$assistantNameLower "
        )
        for (prefix in prefixesToStrip) {
            if (cleanCommand.startsWith(prefix)) {
                cleanCommand = cleanCommand.substring(prefix.length).trim()
            }
        }
        if (cleanCommand.endsWith(" $assistantNameLower")) {
            cleanCommand = cleanCommand.substring(0, cleanCommand.length - assistantNameLower.length - 1).trim()
        }
        
        // Clean remaining commands like "hey " or "ok " if present
        if (cleanCommand.startsWith("hey ")) {
            cleanCommand = cleanCommand.substring(4).trim()
        }
        if (cleanCommand.startsWith("ok ")) {
            cleanCommand = cleanCommand.substring(3).trim()
        }

        // Check if user wants to close mic completely
        val closeMicPhrases = listOf("close the mic", "close mic", "stop listening", "turn off microphone", "turn off mic")
        if (closeMicPhrases.any { cleanCommand.contains(it) }) {
            speak("Closing microphone.")
            log("System: Closing microphone.")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                JarvisSpeechListener.getInstance()?.stopListening()
            }
            return
        }

        // Check if user wants to enter Standby Mode
        val stopRespondingPhrases = listOf(
            "please stop responding", "stop responding", "stop talking",
            "go to standby", "standby mode", "go to sleep", "sleep now", "sleep mode"
        )
        if (stopRespondingPhrases.any { cleanCommand.contains(it) }) {
            _isConversing.value = false
            inactivityJob?.cancel()
            speak(getSleepGreeting())
            log("System: Entered standby mode.")
            return
        }

        // If in standby mode, we only listen for the wake word
        if (!_isConversing.value) {
            val isWakeCommand = cleanCommand == "wake up" ||
                    cleanCommand == "hello sir" ||
                    cleanCommand == "wake up $assistantNameLower" ||
                    cleanCommand == assistantNameLower ||
                    cleanCommand == "hey $assistantNameLower" ||
                    cleanCommand == "ok $assistantNameLower" ||
                    cleanCommand == "hello $assistantNameLower" ||
                    cleanCommand == "wake" ||
                    cleanCommand.startsWith("wake up ") ||
                    cleanCommand.startsWith("hello $assistantNameLower") ||
                    cleanCommand.startsWith("hey $assistantNameLower") ||
                    cleanCommand.startsWith("ok $assistantNameLower")
            
            if (isWakeCommand) {
                _isConversing.value = true
                log("System: Wake command detected. Active conversation resumed.")
                resetInactivityTimer(context)
                
                // Recalculate cleanCommand without wake word
                var tempCommand = command.lowercase().trim()
                for (prefix in prefixesToStrip) {
                    if (tempCommand.startsWith(prefix)) {
                        tempCommand = tempCommand.substring(prefix.length).trim()
                    }
                }
                if (tempCommand.endsWith(" $assistantNameLower")) {
                    tempCommand = tempCommand.substring(0, tempCommand.length - assistantNameLower.length - 1).trim()
                }
                if (tempCommand.startsWith("hey ")) tempCommand = tempCommand.substring(4).trim()
                if (tempCommand.startsWith("ok ")) tempCommand = tempCommand.substring(3).trim()
                cleanCommand = tempCommand

                if (cleanCommand.isBlank()) {
                    speak(getWakeGreeting())
                    return
                }
            } else {
                Log.d(TAG, "Ignoring command in Standby Mode: $command")
                return
            }
        } else {
            // Reset the inactivity timer for active commands
            resetInactivityTimer(context)
        }

        if (checkCustomVoiceCommand(context, cleanCommand)) {
            return
        }

        // 1. App Launching & Custom Synonyms (Offline Capable)
        val cameraSynonyms = listOf("camera", "open camera", "launch camera", "take photo", "take a photo", "take picture", "take a picture", "capture photo")
        if (cameraSynonyms.any { cleanCommand == it }) {
            openApp(context, "camera")
            return
        }

        val videoSynonyms = listOf("make video", "record video", "open video recorder", "record a video", "video recorder", "video camera")
        if (videoSynonyms.any { cleanCommand == it }) {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                speak("Opening video recorder.")
                log("System: Opened video recorder.")
            } else {
                openApp(context, "camera")
            }
            return
        }

        val gallerySynonyms = listOf("gallery", "open gallery", "launch gallery", "photos", "open photos")
        if (gallerySynonyms.any { cleanCommand == it }) {
            openApp(context, "gallery")
            return
        }

        val settingsSynonyms = listOf("settings", "open settings", "phone settings", "system settings")
        if (settingsSynonyms.any { cleanCommand == it }) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Opening settings.")
            log("System: Opened settings.")
            return
        }

        val internetSynonyms = listOf(
            "turn on internet", "turn off internet", "internet settings", 
            "turn on wifi", "turn off wifi", "open wifi", "wifi settings",
            "turn on mobile data", "turn off mobile data", "mobile data settings"
        )
        if (internetSynonyms.any { cleanCommand.contains(it) }) {
            try {
                val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
                speak("Opening network settings.")
                log("System: Opened network connectivity settings.")
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    speak("Opening system settings.")
                } catch (ex: Exception) {
                    speak("Failed to open network settings.")
                }
            }
            return
        }

        val dialerSynonyms = listOf("dialer", "open dialer", "phone", "open phone", "call history")
        if (dialerSynonyms.any { cleanCommand == it }) {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Opening phone dialer.")
            log("System: Opened dialer.")
            return
        }

        // A. Grid Selection overlay commands
        if (cleanCommand == "show grid") {
            JarvisAccessibilityService.showGridOverlay()
            speak("Showing selection grid.")
            log("System: Grid overlay activated.")
            return
        }
        if (cleanCommand == "hide grid" || cleanCommand == "remove grid") {
            JarvisAccessibilityService.hideGridOverlay()
            speak("Hiding selection grid.")
            log("System: Grid overlay deactivated.")
            return
        }
        val gridNumber = cleanCommand.toIntOrNull()
        if (gridNumber != null && gridNumber in 1..9) {
            val success = JarvisAccessibilityService.selectGridCell(gridNumber)
            if (success) {
                speak("Zooming into cell $gridNumber.")
                log("System: Zoomed grid into cell $gridNumber.")
            } else {
                speak("Grid selection is not active.")
            }
            return
        }
        if (cleanCommand.startsWith("tap ") || cleanCommand.startsWith("click ")) {
            val tail = cleanCommand.substringAfter(" ").trim()
            val num = tail.toIntOrNull()
            if (num != null && num in 1..9) {
                val success = JarvisAccessibilityService.clickGridCell(num)
                if (success) {
                    speak("Tapping cell $num.")
                    log("System: Tapped grid coordinates at cell $num.")
                } else {
                    speak("Grid selection is not active.")
                }
                return
            }
        }
        if (cleanCommand.startsWith("long press ")) {
            val tail = cleanCommand.substring("long press ".length).trim()
            val num = tail.toIntOrNull()
            if (num != null && num in 1..9) {
                val success = JarvisAccessibilityService.longClickGridCell(num)
                if (success) {
                    speak("Long pressing cell $num.")
                    log("System: Long pressed grid coordinates at cell $num.")
                } else {
                    speak("Grid selection is not active.")
                }
                return
            }
        }
        if (cleanCommand.startsWith("swipe ") && (cleanCommand.contains(" up") || cleanCommand.contains(" down") || cleanCommand.contains(" left") || cleanCommand.contains(" right"))) {
            val parts = cleanCommand.split(" ")
            if (parts.size >= 3) {
                val direction = parts[1]
                val num = parts[2].toIntOrNull()
                if (num != null && num in 1..9) {
                    val success = JarvisAccessibilityService.swipeFromGridCell(num, direction)
                    if (success) {
                        speak("Swiping $direction from cell $num.")
                        log("System: Dispatched grid swipe $direction starting at cell $num.")
                    } else {
                        speak("Grid selection is not active.")
                    }
                    return
                }
            }
        }

        // B. Touch & Swipe Gestures
        if (cleanCommand.startsWith("long press ")) {
            val target = cleanCommand.substring("long press ".length).trim()
            val success = JarvisAccessibilityService.performLongClickOnText(target)
            if (success) {
                speak("Long pressed $target.")
                log("System: Long clicked text label: \"$target\"")
            } else {
                speak("Could not find $target to long press.")
            }
            return
        }
        if (cleanCommand.startsWith("check ")) {
            val target = cleanCommand.substring("check ".length).trim()
            val success = JarvisAccessibilityService.performCheckOnText(target, true)
            if (success) {
                speak("Checked $target.")
                log("System: Toggled checked state to true for: \"$target\"")
            } else {
                speak("Could not find checkbox $target.")
            }
            return
        }
        if (cleanCommand.startsWith("uncheck ")) {
            val target = cleanCommand.substring("uncheck ".length).trim()
            val success = JarvisAccessibilityService.performCheckOnText(target, false)
            if (success) {
                speak("Unchecked $target.")
                log("System: Toggled checked state to false for: \"$target\"")
            } else {
                speak("Could not find checkbox $target.")
            }
            return
        }
        if (cleanCommand == "scroll down" || cleanCommand == "scroll up" || cleanCommand == "scroll left" || cleanCommand == "scroll right") {
            val dir = cleanCommand.substring("scroll ".length).trim()
            val success = JarvisAccessibilityService.performScrollDirection(dir)
            if (success) {
                speak("Scrolling $dir.")
                log("System: Scrolled screen $dir.")
            } else {
                speak("Failed to scroll.")
            }
            return
        }
        if (cleanCommand.startsWith("swipe ") && cleanCommand.endsWith(" edge")) {
            val edge = cleanCommand.substring("swipe ".length).removeSuffix(" edge").trim()
            val metrics = context.resources.displayMetrics
            val w = metrics.widthPixels.toFloat()
            val h = metrics.heightPixels.toFloat()
            val success = when (edge) {
                "left" -> JarvisAccessibilityService.swipeGesture(0f, h / 2, w * 0.6f, h / 2)
                "right" -> JarvisAccessibilityService.swipeGesture(w, h / 2, w * 0.4f, h / 2)
                "top" -> JarvisAccessibilityService.swipeGesture(w / 2, 0f, w / 2, h * 0.6f)
                "bottom" -> JarvisAccessibilityService.swipeGesture(w / 2, h, w / 2, h * 0.4f)
                else -> false
            }
            if (success) {
                speak("Swiping $edge edge.")
                log("System: Swiped from $edge screen edge.")
            } else {
                speak("Failed to perform edge swipe.")
            }
            return
        }

        // C. Magnification commands
        val magCommands = listOf("start zooming", "zoom in", "zoom out", "stop zooming", "pan left", "pan right", "pan up", "pan down")
        if (magCommands.any { cleanCommand == it }) {
            val success = JarvisAccessibilityService.performMagnification(cleanCommand)
            if (success) {
                speak("Magnifier action completed.")
                log("System: Magnifier command \"$cleanCommand\" executed.")
            } else {
                speak("Magnification control failed.")
            }
            return
        }

        // D. Text Editing commands
        val textEditPrefixes = listOf(
            "delete all text", "delete all", "delete selected text", "delete to the beginning", "delete to the end",
            "select all text", "select all", "unselect text", "go to the beginning", "go to the end", "delete ", "replace ",
            "uppercase ", "lowercase ", "capitalize ", "insert "
        )
        if (textEditPrefixes.any { cleanCommand.startsWith(it) }) {
            val success = JarvisAccessibilityService.performTextEditAction(command)
            if (success) {
                speak("Text updated.")
                log("System: Text editing action \"$command\" executed.")
            } else {
                speak("No focused text field found to edit.")
            }
            return
        }

        // E. System Controls
        if (cleanCommand == "volume up") {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
            speak("Volume increased.")
            log("System: Raised system media volume.")
            return
        }
        if (cleanCommand == "volume down") {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
            speak("Volume decreased.")
            log("System: Lowered system media volume.")
            return
        }
        if (cleanCommand == "mute") {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, android.media.AudioManager.FLAG_SHOW_UI)
            } else {
                audioManager.setStreamMute(android.media.AudioManager.STREAM_MUSIC, true)
            }
            speak("System muted.")
            log("System: Muted system media stream.")
            return
        }
        if (cleanCommand == "unmute") {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, android.media.AudioManager.FLAG_SHOW_UI)
            } else {
                audioManager.setStreamMute(android.media.AudioManager.STREAM_MUSIC, false)
            }
            speak("System unmuted.")
            log("System: Unmuted system media stream.")
            return
        }
        if (cleanCommand == "turn on bluetooth" || cleanCommand == "bluetooth settings" || cleanCommand == "open bluetooth") {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            speak("Opening bluetooth settings.")
            log("System: Launched Bluetooth settings panel.")
            return
        }
        if (cleanCommand == "take screenshot" || cleanCommand == "screenshot") {
            val success = JarvisAccessibilityService.performScreenshot()
            if (success) {
                speak("Taking screenshot.")
                log("System: Triggered screenshot global action.")
            } else {
                speak("Failed to take screenshot.")
            }
            return
        }
        if (cleanCommand == "lock screen" || cleanCommand == "lock") {
            val success = JarvisAccessibilityService.performLockScreen()
            if (success) {
                speak("Locking screen.")
                log("System: Triggered lock screen global action.")
            } else {
                speak("Failed to lock screen.")
            }
            return
        }
        if (cleanCommand == "recent apps" || cleanCommand == "recents") {
            val success = JarvisAccessibilityService.performRecents()
            if (success) {
                speak("Opening recents.")
                log("System: Opened recent applications list.")
            } else {
                speak("Failed to open recents.")
            }
            return
        }
        if (cleanCommand == "quick settings") {
            val success = JarvisAccessibilityService.performQuickSettings()
            if (success) {
                speak("Opening quick settings.")
                log("System: Opened quick settings shade.")
            } else {
                speak("Failed to open quick settings.")
            }
            return
        }
        if (cleanCommand == "open notifications" || cleanCommand == "notifications") {
            val success = JarvisAccessibilityService.performNotifications()
            if (success) {
                speak("Opening notifications.")
                log("System: Opened notifications shade.")
            } else {
                speak("Failed to open notifications.")
            }
            return
        }
        if (cleanCommand == "open power menu" || cleanCommand == "power menu") {
            val success = JarvisAccessibilityService.performPowerMenu()
            if (success) {
                speak("Opening power menu.")
                log("System: Opened system power dialog.")
            } else {
                speak("Failed to open power menu.")
            }
            return
        }

        // Generic Open App command matching
        val openPrefixes = listOf("open ", "launch ", "start ", "go to ", "run ")
        for (prefix in openPrefixes) {
            if (cleanCommand.startsWith(prefix)) {
                val appName = cleanCommand.substring(prefix.length)
                openApp(context, appName)
                return
            }
        }

        // 2. "click [text]" (Supports natural click commands offline)
        var clickTarget: String? = null
        when {
            cleanCommand.startsWith("click on ") -> {
                clickTarget = cleanCommand.substring(9).trim()
            }
            cleanCommand.startsWith("click ") -> {
                clickTarget = cleanCommand.substring(6).trim()
            }
            cleanCommand.startsWith("find ") && (cleanCommand.endsWith(" and click it") || cleanCommand.endsWith(" button and click it")) -> {
                val temp = cleanCommand.substring(5)
                clickTarget = temp.replace(Regex("(?i) button and click it$"), "")
                                  .replace(Regex("(?i) and click it$"), "")
                                  .trim()
            }
            cleanCommand.startsWith("found ") && (cleanCommand.endsWith(" and click it") || cleanCommand.endsWith(" button and click it")) -> {
                val temp = cleanCommand.substring(6)
                clickTarget = temp.replace(Regex("(?i) button and click it$"), "")
                                  .replace(Regex("(?i) and click it$"), "")
                                  .trim()
            }
        }

        if (clickTarget != null && clickTarget.isNotBlank()) {
            val clicked = JarvisAccessibilityService.performClickOnText(clickTarget)
            if (clicked) {
                speak("Clicked $clickTarget")
                log("System: Clicked element containing \"$clickTarget\"")
            } else {
                speak("Could not find button $clickTarget")
                log("System: Click failed for \"$clickTarget\"")
            }
            return
        }

        // 3. "go back"
        if (cleanCommand == "go back" || cleanCommand == "back") {
            val success = JarvisAccessibilityService.performBack()
            if (success) {
                speak("Going back.")
                log("System: Navigated back.")
            } else {
                speak("Failed to navigate back.")
            }
            return
        }

        // 4. "exit app" or "go home"
        if (cleanCommand == "exit app" || cleanCommand == "go home" || cleanCommand == "exit") {
            val success = JarvisAccessibilityService.performHome()
            if (success) {
                speak("Exiting app.")
                log("System: Navigated to home screen.")
            }
            return
        }

        // 5. "read screen"
        if (cleanCommand == "read screen" || cleanCommand == "what is on my screen" || cleanCommand == "read my screen") {
            val content = JarvisAccessibilityService.getScreenContent()
            log("Screen Content:\n$content")
            if (content.isNotBlank() && !content.contains("is not running") && !content.contains("is empty")) {
                val cleanText = getCleanScreenText(content)
                val brief = if (cleanText.length > 500) cleanText.substring(0, 497) + "..." else cleanText
                speak("Here is what I see on the screen: $brief")
            } else {
                speak("I cannot read the screen right now. Please make sure the Jarvis Accessibility Service is active.")
            }
            return
        }

        // 6. "type [text]"
        if (cleanCommand.startsWith("type ")) {
            val lowerCommand = command.lowercase()
            val index = lowerCommand.indexOf("type ")
            val textToType = if (index != -1) {
                command.substring(index + 5).trim()
            } else {
                cleanCommand.substring(5).trim()
            }
            val typed = typeText(textToType)
            if (typed) {
                speak("Typed $textToType")
                log("System: Typed \"$textToType\" into focused field.")
            } else {
                speak("Failed to type. Ensure an input field is focused.")
            }
            return
        }

        // 7. "make a call to [number]"
        if (cleanCommand.startsWith("make a call to ") || cleanCommand.startsWith("call ")) {
            val target = cleanCommand.substringAfter("call ").trim()
            makeCall(context, target)
            return
        }

        // 8. Lock screen unlock by voice PIN
        if (cleanCommand.startsWith("enter password ") || cleanCommand.startsWith("enter pin ") || cleanCommand.startsWith("unlock phone ") || cleanCommand.startsWith("unlock device ")) {
            val digits = cleanCommand.filter { it.isDigit() }
            if (digits.isNotBlank()) {
                speak("Attempting to unlock, ${userName.value}.")
                val success = JarvisAccessibilityService.enterLockScreenPin(digits)
                if (success) {
                    log("System: Simulating passcode clicks on lock screen.")
                } else {
                    log("System: Attempted keypress simulation for passcode.")
                }
            } else {
                speak("Please dictate the passcode digits, ${userName.value}.")
            }
            return
        }

        // 9. Image Enhancement command
        val isEnhanceCmd = cleanCommand.contains("enhance picture") || 
                           cleanCommand.contains("enhance photo") || 
                           cleanCommand.contains("enhance this picture") ||
                           cleanCommand.contains("enhance this photo") ||
                           cleanCommand.contains("enhance image") ||
                           cleanCommand.contains("enhance this image")
        if (isEnhanceCmd) {
            if (!isNetworkAvailable(context)) {
                speak("Please turn on internet first, ${userName.value}.")
                log("System: Image enhancement requires internet connection.")
            } else {
                speak("Accessing gallery image...")
                val latestUri = getSelectedOrLatestGalleryImage(context)
                if (latestUri != null) {
                    log("System: Found photo to enhance: $latestUri. Enhancing image clarity...")
                    val enhancedUri = enhanceImage(context, latestUri)
                    if (enhancedUri != null) {
                        speak("Image successfully enhanced and saved to gallery.")
                        log("System: Image enhanced successfully. Saved at: $enhancedUri")
                    } else {
                        speak("I encountered an error during image processing, ${userName.value}.")
                    }
                } else {
                    speak("I could not find any recent photos in your gallery, ${userName.value}.")
                    log("System: No recent photos found.")
                }
            }
            return
        }

        // 10. Greeting check (Offline Capable)
        val greetingSynonyms = listOf("hello", "hi", "hey", "hello $assistantNameLower", "hi $assistantNameLower", "hey $assistantNameLower")
        if (greetingSynonyms.any { cleanCommand == it }) {
            speak("Hello, ${userName.value}. How can I assist you?")
            log("System: Handled greeting locally.")
            return
        }

        // Voice Access Require Verbs check
        val requireVerbs = prefs.getBoolean("require_verbs", true)
        if (!requireVerbs) {
            val clicked = JarvisAccessibilityService.performClickOnText(cleanCommand)
            if (clicked) {
                speak("Clicked $cleanCommand")
                log("System: Click fallback for element: \"$cleanCommand\"")
                return
            }
        }

        // 11. Default: Ask Gemini AI for intelligent action
        val queryForGemini = cleanCommand
            .replace(assistantNameLower, "")
            .replace("hey ", "")
            .replace("ok ", "")
            .trim()
            
        if (queryForGemini.isNotBlank()) {
            if (!isNetworkAvailable(context)) {
                speak("I am offline right now, ${userName.value}. Please turn on internet first.")
                log("System: Gemini mainframe is offline.")
            } else {
                askGemini(queryForGemini)
            }
        } else {
            speak("Yes, ${userName.value}?")
            log("System: Wake word detected without query.")
        }
    }

    private fun openApp(context: Context, appName: String) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        val normalizedQuery = appName.lowercase()
            .replace("yt", "youtube")
            .replace("whats app", "whatsapp")
            .replace("pub g", "pubg")
            .trim()

        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            val normalizedLabel = label.replace("whats app", "whatsapp").replace("pub g", "pubg")
            
            if (label.contains(normalizedQuery) || normalizedLabel.contains(normalizedQuery) || normalizedQuery.contains(label)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    speak("Opening $label")
                    log("System: Opened app $label (${app.packageName})")
                    recordAppLaunch(context, label)
                    return
                }
            }
        }
        speak("I could not find application $appName installed on this device.")
        log("System: App $appName not found.")
    }

    private fun recordAppLaunch(context: Context, appLabel: String) {
        val prefs = context.getSharedPreferences("jarvis_learning", Context.MODE_PRIVATE)
        val count = prefs.getInt(appLabel, 0) + 1
        prefs.edit().putInt(appLabel, count).apply()
        log("System: Learning - App '$appLabel' has been launched $count times.")
    }

    private fun getCleanScreenText(content: String): String {
        return content.lines()
            .map { it.replace(Regex("""^\s*-\s*"""), "").replace("[Clickable]", "").trim() }
            .filter { it.isNotBlank() && !it.contains("Accessibility Service") && !it.contains("is not running") && !it.contains("is empty") }
            .joinToString(", ")
    }

    private fun typeText(text: String): Boolean {
        val service = JarvisAccessibilityService.getInstance() ?: return false
        
        // Try rootInActiveWindow first
        val root = service.rootInActiveWindow
        if (root != null) {
            val focusedNode = findFocusedNode(root)
            if (focusedNode != null) {
                val arguments = android.os.Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                focusedNode.recycle()
                root.recycle()
                return success
            }
            root.recycle()
        }
        
        // Fall back to all interactive windows
        val windows = service.windows
        if (!windows.isNullOrEmpty()) {
            for (window in windows) {
                val wRoot = window.root
                if (wRoot != null) {
                    val focusedNode = findFocusedNode(wRoot)
                    if (focusedNode != null) {
                        val arguments = android.os.Bundle()
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                        val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        focusedNode.recycle()
                        wRoot.recycle()
                        return success
                    }
                    wRoot.recycle()
                }
            }
        }
        return false
    }

    private fun findFocusedNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedNode(child)
            if (result != null) {
                if (result != child) child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }

    private fun makeCall(context: Context, target: String) {
        // Check if it's numeric, or name
        val uriString = if (target.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
            "tel:${target.replace(" ", "")}"
        } else {
            // Can add contact database resolution here. For now, open dialer with input
            "tel:"
        }
        val intent = Intent(Intent.ACTION_CALL, Uri.parse(uriString)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            speak("Calling $target")
            log("System: Initiating call to $target")
        } catch (e: SecurityException) {
            // Fallback to dialer if CALL_PHONE permission is not granted
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse(uriString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            speak("Opening dialer for $target")
            log("System: Opened dialer (Call permission missing)")
        }
    }

    private fun askGemini(query: String) {
        log("${assistantName.value} AI: Analyzing request...")
        scope.launch {
            JarvisSpeechListener.pauseTemporarily()
            val screenContent = JarvisAccessibilityService.getScreenContent()
            val hasScreen = screenContent.isNotBlank() && 
                            !screenContent.contains("is not running") && 
                            !screenContent.contains("is empty") && 
                            !screenContent.contains("not accessible")
            
            val contextPrompt = if (hasScreen) {
                "The user is looking at a phone screen. Here is the current screen content parsed by accessibility services:\n" +
                "```\n$screenContent\n```\n" +
                "The user's command or question is: \"$query\".\n" +
                "If the user asks you to read the screen, summarize what is on the screen in a brief, friendly way.\n" +
                "If the user asks you to find a button/link/text and click it (or perform an action on it), reply with a special action payload so the app can click it.\n" +
                "Format your reply as JSON ONLY if you want to click a button, like this:\n" +
                "{\n  \"thought\": \"I will click the [button text] button for you.\",\n  \"action\": \"CLICK\",\n  \"target\": \"[exact text of element on screen]\"\n}\n" +
                "Otherwise, respond with a direct natural text answer."
            } else {
                query
            }
            
            val response = GeminiClient.generateResponse(contextPrompt)
            val cleanResponse = response.trim()
            
            // Check if response is JSON containing click action
            if (cleanResponse.startsWith("{") && cleanResponse.endsWith("}")) {
                try {
                    val json = org.json.JSONObject(cleanResponse)
                    val action = json.optString("action", "")
                    val target = json.optString("target", "")
                    val thought = json.optString("thought", "")
                    
                    if (action.uppercase() == "CLICK" && target.isNotBlank()) {
                        if (thought.isNotBlank()) {
                            log("${assistantName.value}: \"$thought\"")
                            speak(thought)
                        } else {
                            speak("Clicking $target.")
                        }
                        val clicked = JarvisAccessibilityService.performClickOnText(target)
                        if (clicked) {
                            log("System: Clicked $target based on Gemini action.")
                        } else {
                            speak("I found the $target element but was unable to click it.")
                            log("System: Click failed for $target from Gemini action.")
                        }
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing Gemini action JSON", e)
                }
            }
            
            log("${assistantName.value}: \"$response\"")
            speak(response)
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(activeNetwork) ?: return false
            return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            return info != null && info.isConnected
        }
    }

    private fun getLatestGalleryImage(context: Context): Uri? {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )
        query?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
            }
        }
        return null
    }

    private fun enhanceImage(context: Context, uri: Uri): Uri? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream) ?: return null
            val enhanced = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(enhanced)
            val paint = Paint()
            
            val contrast = 1.25f
            val brightness = 12f
            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(original, 0f, 0f, paint)
            
            val filename = "Enhanced_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JarvisEnhanced")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out ->
                enhanced.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            return itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing image", e)
            return null
        }
    }

    private fun getSelectedOrLatestGalleryImage(context: Context): Uri? {
        val screenContent = JarvisAccessibilityService.getScreenContent()
        val filenameRegex = Regex("""(?i)(?:IMG_|PANO_|Screenshot_|DSC_)[\w\-\.]+|[\w\-\.]+\.(?:jpg|jpeg|png|webp)""")
        val matches = filenameRegex.findAll(screenContent).map { it.value.trim() }.toList()
        
        for (match in matches) {
            log("System: Found potential filename on screen: $match")
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$match%")
            val query = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )
            query?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val id = cursor.getLong(idColumn)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val foundName = cursor.getString(nameColumn)
                    log("System: Successfully matched screen text '$match' to MediaStore file '$foundName'")
                    return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
        }
        
        log("System: No filename matched on screen. Falling back to the latest gallery photo.")
        return getLatestGalleryImage(context)
    }
}
