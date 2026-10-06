package com.example.jarvis_personalassistant.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.example.jarvis_personalassistant.*
import com.example.jarvis_personalassistant.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize TTS and GeminiClient settings
    LaunchedEffect(Unit) {
        JarvisEngine.init(context)
        GeminiClient.init(context)
    }

    // Permissions check
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        storagePermission
    )

    var hasPermissions by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (!hasPermissions) {
            Toast.makeText(context, "Permissions required for assistant capabilities.", Toast.LENGTH_SHORT).show()
        }
    }

    // State bindings from JarvisEngine
    val userName by JarvisEngine.userName.collectAsStateWithLifecycle()
    val assistantName by JarvisEngine.assistantName.collectAsStateWithLifecycle()
    val selectedLanguage by JarvisEngine.selectedLanguage.collectAsStateWithLifecycle()
    val workOnLockScreen by JarvisEngine.workOnLockScreen.collectAsStateWithLifecycle()

    val isAccessibilityActive by JarvisAccessibilityService.isRunning.collectAsStateWithLifecycle()
    val isSpeechListening by JarvisSpeechListener.isListening.collectAsStateWithLifecycle()
    val isConversing by JarvisEngine.isConversing.collectAsStateWithLifecycle()
    val speechStatus by JarvisEngine.speechStatus.collectAsStateWithLifecycle()
    val detectedFaceExpression by JarvisEngine.faceExpression.collectAsStateWithLifecycle()
    val latestVoiceText by JarvisEngine.latestVoiceText.collectAsStateWithLifecycle()

    val speechListener = remember { JarvisSpeechListener.getInstance(context) }

    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            speechListener.startListening()
        }
    }

    // Face Recognition Toggle
    val isFaceActive by JarvisEngine.isFaceTrackingActive.collectAsStateWithLifecycle()

    LaunchedEffect(isFaceActive) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        if (isFaceActive) {
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build().apply {
                            setAnalyzer(ContextCompat.getMainExecutor(context), JarvisFaceAnalyzer())
                        }
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
                } catch (exc: Exception) {
                    Log.e("MainScreen", "Silent camera binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        } else {
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                } catch (exc: Exception) {
                    Log.e("MainScreen", "Unbinding camera failed", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    // Logs Console state
    val logs by JarvisEngine.logs.collectAsStateWithLifecycle()
    val logsListState = rememberLazyListState()

    val themeMode by JarvisEngine.themeMode.collectAsStateWithLifecycle()
    val fontColorPreset by JarvisEngine.fontColorPreset.collectAsStateWithLifecycle()
    val iconColorPreset by JarvisEngine.iconColorPreset.collectAsStateWithLifecycle()

    var selectedThemeMode by remember(themeMode) { mutableStateOf(themeMode) }
    var selectedFontColor by remember(fontColorPreset) { mutableStateOf(fontColorPreset) }
    var selectedIconColor by remember(iconColorPreset) { mutableStateOf(iconColorPreset) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            logsListState.animateScrollToItem(logs.size - 1)
        }
    }

    // Customization fields (Local state)
    var userNameInput by remember(userName) { mutableStateOf(userName) }
    var assistantNameInput by remember(assistantName) { mutableStateOf(assistantName) }
    var langCodeSelected by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }
    val initialKey = remember {
        context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: ""
    }
    var geminiApiKeyInput by remember { mutableStateOf(initialKey) }

    // Language Dropdown expanded state
    var langExpanded by remember { mutableStateOf(false) }
    val languages = listOf(
        Pair("English", "en"),
        Pair("Arabic", "ar"),
        Pair("German", "de"),
        Pair("Spanish", "es"),
        Pair("Hindi", "hi"),
        Pair("Punjabi", "pa"),
        Pair("Urdu", "ur"),
        Pair("Malayalam", "ml"),
        Pair("Tamil", "ta"),
        Pair("Bangla", "bn"),
        Pair("Nepali", "ne")
    )
    val activeLangName = languages.find { it.second == langCodeSelected }?.first ?: "English"

    // Voice Calibration Flow
    var calibrationStep by remember { mutableStateOf(0) } // 0 = Idle, 1 = Say Hello, 2 = Say Read Screen, 3 = Say Go Back, 4 = Success
    var calibrationLog by remember { mutableStateOf("Press START to calibrate voice profile.") }
    
    // Check if voice is already calibrated
    val prefs = remember { context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE) }
    var isCalibrated by remember { mutableStateOf(prefs.getBoolean("voice_calibrated", false)) }
    
    var requireVerbs by remember { mutableStateOf(prefs.getBoolean("require_verbs", true)) }
    var inactivityTimeoutMs by remember { mutableStateOf(prefs.getLong("inactivity_timeout_ms", 40000L)) }
    var incomingCallWake by remember { mutableStateOf(prefs.getBoolean("incoming_call_wake", false)) }
    var activeDuringCalls by remember { mutableStateOf(prefs.getBoolean("active_during_calls", false)) }
    var recognizeCommonIcons by remember { mutableStateOf(prefs.getBoolean("recognize_common_icons", true)) }
    var activationKeyEnabled by remember { mutableStateOf(prefs.getBoolean("activation_key_enabled", false)) }



    // Custom Voice Commands
    var customVoiceCommandsJson by remember { mutableStateOf(prefs.getString("custom_voice_commands", "[]") ?: "[]") }
    val customCommandsList = remember(customVoiceCommandsJson) {
        val list = mutableListOf<Triple<String, String, String>>()
        try {
            val arr = org.json.JSONArray(customVoiceCommandsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Triple(
                    obj.optString("trigger", ""),
                    obj.optString("action", ""),
                    obj.optString("value", "")
                ))
            }
        } catch (e: Exception) {
            Log.e("MainScreen", "Error parsing custom voice commands", e)
        }
        list
    }
    var newTriggerText by remember { mutableStateOf("") }
    var newActionType by remember { mutableStateOf("OPEN_APP") }
    var newActionValue by remember { mutableStateOf("") }
    var newActionExpanded by remember { mutableStateOf(false) }

    val installedApps = remember {
        try {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { app -> pm.getApplicationLabel(app).toString() }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            emptyList<String>()
        }
    }

    val calibrationPitches = remember { mutableStateListOf<Float>() }
    val calibrationEmbeddings = remember { mutableStateListOf<FloatArray>() }

    // Voice training word matching logic
    LaunchedEffect(latestVoiceText) {
        if (calibrationStep in 1..3 && latestVoiceText.isNotBlank()) {
            val textClean = latestVoiceText.lowercase().trim()
            val assName = assistantName.lowercase()
            when (calibrationStep) {
                1 -> {
                    if (textClean.contains("hello") || textClean.contains(assName)) {
                        val pitch = JarvisSpeechListener.getAverageCollectedPitch()
                        if (pitch > 0f) {
                            calibrationPitches.add(pitch)
                        }
                        val embedding = JarvisSpeechListener.getAverageUtteranceEmbedding()
                        if (embedding != null) {
                            calibrationEmbeddings.add(embedding)
                        }
                        calibrationLog = "Phrase matched: \"$latestVoiceText\". Calibrating step 2..."
                        JarvisEngine.speak("Phrase matched, preparing next sequence.")
                        calibrationStep = 2
                    } else {
                        calibrationLog = "Heard: \"$latestVoiceText\". Say: \"Hello $assistantName\""
                    }
                }
                2 -> {
                    if (textClean.contains("read") || textClean.contains("screen")) {
                        val pitch = JarvisSpeechListener.getAverageCollectedPitch()
                        if (pitch > 0f) {
                            calibrationPitches.add(pitch)
                        }
                        val embedding = JarvisSpeechListener.getAverageUtteranceEmbedding()
                        if (embedding != null) {
                            calibrationEmbeddings.add(embedding)
                        }
                        calibrationLog = "Phrase matched: \"$latestVoiceText\". Calibrating step 3..."
                        JarvisEngine.speak("Acoustic node synchronized. Final sequence.")
                        calibrationStep = 3
                    } else {
                        calibrationLog = "Heard: \"$latestVoiceText\". Say: \"$assistantName, read my screen\""
                    }
                }
                3 -> {
                    if (textClean.contains("back") || textClean.contains("go")) {
                        val pitch = JarvisSpeechListener.getAverageCollectedPitch()
                        if (pitch > 0f) {
                            calibrationPitches.add(pitch)
                        }
                        val embedding = JarvisSpeechListener.getAverageUtteranceEmbedding()
                        if (embedding != null) {
                            calibrationEmbeddings.add(embedding)
                        }
                        
                        val finalPitch = if (calibrationPitches.isNotEmpty()) calibrationPitches.average().toFloat() else 0f
                        if (finalPitch > 0f) {
                            prefs.edit().putFloat("voice_pitch_target", finalPitch).apply()
                            JarvisEngine.setVoicePitchTarget(finalPitch)
                            Log.d("MainScreen", "Voice target pitch matched and saved: $finalPitch Hz")
                        }

                        val finalEmbedding = if (calibrationEmbeddings.isNotEmpty()) {
                            VoiceBiometrics.averageEmbeddings(calibrationEmbeddings)
                        } else {
                            null
                        }
                        if (finalEmbedding != null) {
                            JarvisEngine.setVoiceEmbeddingTarget(context, finalEmbedding)
                            Log.d("MainScreen", "Voice target embedding matched and saved: ${finalEmbedding.joinToString()}")
                        }

                        if (finalPitch > 0f || finalEmbedding != null) {
                            calibrationLog = "Acoustic matching completed successfully!"
                            JarvisEngine.speak("Calibration complete, $userName. Voice template loaded.")
                        } else {
                            calibrationLog = "Calibration completed. Note: Standard speech mode active."
                            JarvisEngine.speak("Calibration complete, $userName.")
                        }
                        calibrationStep = 4
                        isCalibrated = true
                        prefs.edit().putBoolean("voice_calibrated", true).apply()
                    } else {
                        calibrationLog = "Heard: \"$latestVoiceText\". Say: \"$assistantName, go back\""
                    }
                }
            }
        }
    }

    // Core pulsing HUD ring animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val coreScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val coreRotate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isAccessibilityActive) JarvisPrimary else JarvisError, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCalibrated) assistantName.uppercase() else "SIRI VOICE TRAINING",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = JarvisPrimary,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    if (isCalibrated) {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = JarvisPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JarvisBackground)
            )
        },
        containerColor = JarvisBackground
    ) { paddingValues ->
        if (!isCalibrated) {
            VoiceTrainingLayout(
                paddingValues = paddingValues,
                assistantName = assistantName,
                userName = userName,
                hasPermissions = hasPermissions,
                calibrationStep = calibrationStep,
                calibrationLog = calibrationLog,
                launcher = launcher,
                permissions = permissions,
                isSpeechListening = isSpeechListening,
                speechListener = speechListener,
                onStartCalibration = {
                    calibrationStep = 1
                    calibrationLog = "Listening... Say: \"Hello $assistantName\""
                    calibrationPitches.clear()
                    calibrationEmbeddings.clear()
                    JarvisSpeechListener.clearCollectedPitches()
                    speechListener.startListening()
                },
                onCancelCalibration = {
                    calibrationStep = 0
                    if (isSpeechListening) speechListener.stopListening()
                },
                onFinishCalibration = {
                    calibrationStep = 0
                    isCalibrated = true
                }
            )
        } else {
            Column(
                modifier = modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                // Scrollable Settings & Config HUD
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // 1. ARC REACTOR CORE VISUALIZER
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .padding(8.dp)
                            .drawBehind {
                                drawCircle(
                                    color = JarvisPrimary.copy(alpha = 0.1f),
                                    radius = size.minDimension / 2,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(
                                    color = JarvisSecondary.copy(alpha = 0.05f),
                                    radius = size.minDimension / 3,
                                    style = Stroke(width = 6.dp.toPx())
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .rotate(coreRotate)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.sweepGradient(
                                            listOf(JarvisPrimary, JarvisSecondary, Color.Transparent, JarvisPrimary)
                                        ),
                                        radius = size.minDimension / 2,
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                        )
                        Box(
                            modifier = Modifier
                                .size(50.dp * coreScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(JarvisPrimary, JarvisSecondary.copy(alpha = 0.5f), Color.Transparent)
                                    )
                                )
                                .border(2.dp, if (isSpeechListening) JarvisAccent else JarvisPrimary, CircleShape)
                        )
                    }

                    Text(
                        text = speechStatus.uppercase(),
                        color = if (isSpeechListening) JarvisAccent else JarvisPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. SYSTEM MODULES & SWITCHES
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JarvisPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "MAINFRAME STATUS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Accessibility switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Accessibility Node Parser", fontWeight = FontWeight.Bold, color = JarvisTextPrimary, fontSize = 14.sp)
                                    Text(
                                        text = if (isAccessibilityActive) "ONLINE" else "OFFLINE (Tap to activate)",
                                        fontSize = 11.sp,
                                        color = if (isAccessibilityActive) JarvisAccent else JarvisError,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Switch(
                                    checked = isAccessibilityActive,
                                    onCheckedChange = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = JarvisAccent, checkedTrackColor = JarvisAccent.copy(0.4f))
                                )
                            }

                            Divider(color = JarvisPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))

                            // Voice commands status (read-only)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Voice Command Core", fontWeight = FontWeight.Bold, color = JarvisTextPrimary, fontSize = 14.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isSpeechListening) "ONLINE" else "OFFLINE",
                                            fontSize = 11.sp,
                                            color = if (isSpeechListening) JarvisAccent else JarvisError,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "•",
                                            fontSize = 11.sp,
                                            color = JarvisTextSecondary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isConversing) "ACTIVE LISTENING" else "STANDBY (Sleep)",
                                            fontSize = 11.sp,
                                            color = if (isConversing) JarvisAccent else JarvisTextSecondary,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Divider(color = JarvisPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))

                            // Face tracking switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isFaceActive) {
                                        onItemClick(GestureMappings)
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Face Gesture Tracking", fontWeight = FontWeight.Bold, color = JarvisTextPrimary, fontSize = 14.sp)
                                    Text(
                                        text = if (isFaceActive) "ANALYZING ($detectedFaceExpression - Tap to configure)" else "DISABLED",
                                        fontSize = 11.sp,
                                        color = if (isFaceActive) JarvisAccent else JarvisTextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Switch(
                                    checked = isFaceActive,
                                    onCheckedChange = { active ->
                                        if (!hasPermissions) {
                                            launcher.launch(permissions)
                                        } else {
                                            JarvisEngine.updateFaceTrackingActive(context, active)
                                            if (active) {
                                                onItemClick(GestureMappings)
                                            }
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = JarvisAccent, checkedTrackColor = JarvisAccent.copy(0.4f))
                                )
                            }

                            Divider(color = JarvisPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onItemClick(CommandsReference)
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Voice Commands Reference", fontWeight = FontWeight.Bold, color = JarvisTextPrimary, fontSize = 14.sp)
                                    Text(
                                        text = "View the guide for all voice access controls",
                                        fontSize = 11.sp,
                                        color = JarvisTextSecondary
                                    )
                                }
                                Text("➔", color = JarvisPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            Divider(color = JarvisPrimary.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))

                            // Work when phone is locked switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Work on Lock Screen", fontWeight = FontWeight.Bold, color = JarvisTextPrimary, fontSize = 14.sp)
                                    Text(
                                        text = if (workOnLockScreen) "ENABLED (Voice PIN Unlock)" else "DISABLED",
                                        fontSize = 11.sp,
                                        color = if (workOnLockScreen) JarvisAccent else JarvisTextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Switch(
                                    checked = workOnLockScreen,
                                    onCheckedChange = { active ->
                                        JarvisEngine.updateLockScreenSetting(context, active)
                                        Toast.makeText(context, "Restart app to apply Lock Screen changes.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = JarvisAccent, checkedTrackColor = JarvisAccent.copy(0.4f))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. SYSTEM CUSTOMIZATION CARD (Names & Language)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JarvisPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "SYSTEM CONFIGURATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // User Name Input
                            OutlinedTextField(
                                value = userNameInput,
                                onValueChange = { userNameInput = it },
                                label = { Text("User Call Sign", color = JarvisTextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = JarvisTextPrimary,
                                    unfocusedTextColor = JarvisTextPrimary,
                                    focusedBorderColor = JarvisPrimary,
                                    unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Assistant Name Input
                            OutlinedTextField(
                                value = assistantNameInput,
                                onValueChange = { assistantNameInput = it },
                                label = { Text("Assistant Designation", color = JarvisTextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = JarvisTextPrimary,
                                    unfocusedTextColor = JarvisTextPrimary,
                                    focusedBorderColor = JarvisPrimary,
                                    unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Gemini API Key Input
                            OutlinedTextField(
                                value = geminiApiKeyInput,
                                onValueChange = { geminiApiKeyInput = it },
                                label = { Text("Gemini API Key", color = JarvisTextSecondary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = JarvisTextPrimary,
                                    unfocusedTextColor = JarvisTextPrimary,
                                    focusedBorderColor = JarvisPrimary,
                                    unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Custom Language dropdown selector
                            Text("System Language Locale", color = JarvisTextSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(JarvisBackground, RoundedCornerShape(4.dp))
                                    .border(1.dp, JarvisTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .clickable { langExpanded = !langExpanded }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(activeLangName, color = JarvisTextPrimary, fontSize = 14.sp)
                                    Text("▼", color = JarvisPrimary, fontSize = 12.sp)
                                }
                                DropdownMenu(
                                    expanded = langExpanded,
                                    onDismissRequest = { langExpanded = false },
                                    modifier = Modifier.background(JarvisCardBg)
                                ) {
                                    languages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(lang.first, color = JarvisTextPrimary) },
                                            onClick = {
                                                langCodeSelected = lang.second
                                                langExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    JarvisEngine.updateSettings(context, userNameInput, assistantNameInput, langCodeSelected)
                                    GeminiClient.setApiKey(context, geminiApiKeyInput)
                                    Toast.makeText(context, "System configurations initialized, sir.", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisSecondary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SAVE CONFIGURATIONS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3.5 CONTROL MAPPINGS CARD (Gestures & Custom Commands)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JarvisPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "CONTROL MAPPINGS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(10.dp))



                            // VOICE COMMANDS TUTORIAL/TEACHER
                            Text("Custom Voice Triggers", fontWeight = FontWeight.Bold, color = JarvisPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(8.dp))

                            // List existing custom commands
                            customCommandsList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(JarvisBackground, RoundedCornerShape(4.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Say: \"${item.first}\"", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Do: ${item.second} ${if (item.third.isNotBlank()) "(${item.third})" else ""}", color = JarvisTextSecondary, fontSize = 11.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clickable {
                                                val updated = customCommandsList.toMutableList()
                                                updated.removeAt(index)
                                                val arr = org.json.JSONArray()
                                                for (cmd in updated) {
                                                    arr.put(org.json.JSONObject().apply {
                                                        put("trigger", cmd.first)
                                                        put("action", cmd.second)
                                                        put("value", cmd.third)
                                                    })
                                                }
                                                val jsonStr = arr.toString()
                                                prefs.edit().putString("custom_voice_commands", jsonStr).apply()
                                                customVoiceCommandsJson = jsonStr
                                            }
                                            .padding(4.dp)
                                    ) {
                                        Text("✕", color = JarvisError, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Add new command input form
                            Text("Teach New Voice Trigger", color = JarvisTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = newTriggerText,
                                onValueChange = { newTriggerText = it },
                                label = { Text("When I say...", color = JarvisTextSecondary, fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = JarvisTextPrimary,
                                    unfocusedTextColor = JarvisTextPrimary,
                                    focusedBorderColor = JarvisPrimary,
                                    unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Dropdown for new action type
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(JarvisBackground, RoundedCornerShape(4.dp))
                                    .border(1.dp, JarvisTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .clickable { newActionExpanded = !newActionExpanded }
                                    .padding(10.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(newActionType, color = JarvisTextPrimary, fontSize = 13.sp)
                                    Text("▼", color = JarvisPrimary, fontSize = 10.sp)
                                }
                                DropdownMenu(
                                    expanded = newActionExpanded,
                                    onDismissRequest = { newActionExpanded = false },
                                    modifier = Modifier.background(JarvisCardBg)
                                ) {
                                    listOf("BACK", "HOME", "OPEN_APP", "TYPE_TEXT").forEach { action ->
                                        DropdownMenuItem(
                                            text = { Text(action, color = JarvisTextPrimary) },
                                            onClick = {
                                                newActionType = action
                                                newActionExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            if (newActionType == "OPEN_APP") {
                                var appDropdownExpanded by remember { mutableStateOf(false) }
                                val filteredApps = remember(newActionValue) {
                                    if (newActionValue.isBlank()) {
                                        installedApps
                                    } else {
                                        installedApps.filter { it.contains(newActionValue, ignoreCase = true) }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = newActionValue,
                                        onValueChange = { 
                                            newActionValue = it
                                            appDropdownExpanded = true
                                        },
                                        label = { Text("Select App to Open", color = JarvisTextSecondary, fontSize = 12.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = JarvisTextPrimary,
                                            unfocusedTextColor = JarvisTextPrimary,
                                            focusedBorderColor = JarvisPrimary,
                                            unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownMenu(
                                        expanded = appDropdownExpanded && filteredApps.isNotEmpty(),
                                        onDismissRequest = { appDropdownExpanded = false },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .background(JarvisCardBg)
                                    ) {
                                        filteredApps.forEach { appLabel ->
                                            DropdownMenuItem(
                                                text = { Text(appLabel, color = JarvisTextPrimary) },
                                                onClick = {
                                                    newActionValue = appLabel
                                                    appDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            } else if (newActionType == "TYPE_TEXT") {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newActionValue,
                                    onValueChange = { newActionValue = it },
                                    label = { Text("Text to Type", color = JarvisTextSecondary, fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = JarvisTextPrimary,
                                        unfocusedTextColor = JarvisTextPrimary,
                                        focusedBorderColor = JarvisPrimary,
                                        unfocusedBorderColor = JarvisTextSecondary.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (newTriggerText.isNotBlank()) {
                                        val updated = customCommandsList.toMutableList()
                                        updated.add(Triple(newTriggerText, newActionType, newActionValue))
                                        val arr = org.json.JSONArray()
                                        for (cmd in updated) {
                                            arr.put(org.json.JSONObject().apply {
                                                put("trigger", cmd.first)
                                                put("action", cmd.second)
                                                put("value", cmd.third)
                                            })
                                        }
                                        val jsonStr = arr.toString()
                                        prefs.edit().putString("custom_voice_commands", jsonStr).apply()
                                        customVoiceCommandsJson = jsonStr
                                        // Reset fields
                                        newTriggerText = ""
                                        newActionValue = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("TEACH TRIGGER", color = JarvisBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 5. TERMINAL LOG CONSOLE (Stays fixed at the bottom)
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisBackground),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(bottom = 8.dp)
                        .border(1.dp, JarvisPrimary.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text(
                            "MAINFRAME LOGS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisPrimary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        LazyColumn(
                            state = logsListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("User:")) JarvisPrimary else if (log.contains("System:") || log.contains("Error")) JarvisError else JarvisTerminalText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 0.5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        val ambientLightColor by JarvisEngine.ambientLightColor.collectAsStateWithLifecycle()
        var selectedAmbientColor by remember(ambientLightColor) { mutableStateOf(ambientLightColor) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    "SYSTEM THEME SETTINGS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = JarvisPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Background Mode selector
                    Column {
                        Text("Background Mode", color = JarvisTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("WHITE", "DARK").forEach { mode ->
                                Button(
                                    onClick = { selectedThemeMode = mode },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedThemeMode == mode) JarvisPrimary else JarvisBackground,
                                        contentColor = if (selectedThemeMode == mode) JarvisBackground else JarvisTextPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (mode == "WHITE") "White Mode" else "Darker Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Font & Accent color selector
                    Column {
                        Text("Font & Accent Color", color = JarvisTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val colorPresets = listOf("CYAN", "BLUE", "GREEN", "RED", "YELLOW", "MAGENTA", "PURPLE")
                            colorPresets.forEach { colorName ->
                                val displayColor = when (colorName) {
                                    "CYAN" -> Color(0xFF00E5FF)
                                    "BLUE" -> Color(0xFF0082FF)
                                    "GREEN" -> Color(0xFF00E676)
                                    "RED" -> Color(0xFFFF1744)
                                    "YELLOW" -> Color(0xFFFFEA00)
                                    "MAGENTA" -> Color(0xFFFF007F)
                                    "PURPLE" -> Color(0xFFD500F9)
                                    else -> Color.White
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(displayColor)
                                        .border(
                                            width = if (selectedFontColor == colorName) 3.dp else 1.dp,
                                            color = if (selectedFontColor == colorName) JarvisTextPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedFontColor = colorName }
                                )
                            }
                        }
                    }

                    // App Launcher Icon color selector
                    Column {
                        Text("App Launcher Icon Color", color = JarvisTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val iconPresets = listOf("BLUE", "GREEN", "RED", "YELLOW", "MAGENTA")
                            iconPresets.forEach { colorName ->
                                val displayColor = when (colorName) {
                                    "BLUE" -> Color(0xFF0082FF)
                                    "GREEN" -> Color(0xFF00E676)
                                    "RED" -> Color(0xFFFF1744)
                                    "YELLOW" -> Color(0xFFFFEA00)
                                    "MAGENTA" -> Color(0xFFFF007F)
                                    else -> Color.White
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(displayColor)
                                        .border(
                                            width = if (selectedIconColor == colorName) 3.dp else 1.dp,
                                            color = if (selectedIconColor == colorName) JarvisTextPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedIconColor = colorName }
                                )
                            }
                        }
                    }

                    // Ambient Edge Glow Color selector
                    Column {
                        Text("Ambient Edge Glow Color", color = JarvisTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val ambientPresets = listOf(
                                Pair("CYAN", "#00FFFF"),
                                Pair("BLUE", "#0000FF"),
                                Pair("GREEN", "#00FF00"),
                                Pair("RED", "#FF0000"),
                                Pair("YELLOW", "#FFFF00"),
                                Pair("MAGENTA", "#FF00FF"),
                                Pair("PURPLE", "#800080"),
                                Pair("WHITE", "#FFFFFF")
                            )
                            ambientPresets.forEach { preset ->
                                val displayColor = when (preset.first) {
                                    "CYAN" -> Color(0xFF00FFFF)
                                    "BLUE" -> Color(0xFF0000FF)
                                    "GREEN" -> Color(0xFF00FF00)
                                    "RED" -> Color(0xFFFF0000)
                                    "YELLOW" -> Color(0xFFFFEA00)
                                    "MAGENTA" -> Color(0xFFFF00FF)
                                    "PURPLE" -> Color(0xFF800080)
                                    "WHITE" -> Color(0xFFFFFFFF)
                                    else -> Color.White
                                }
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(displayColor)
                                        .border(
                                            width = if (selectedAmbientColor == preset.second) 3.dp else 1.dp,
                                            color = if (selectedAmbientColor == preset.second) JarvisTextPrimary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedAmbientColor = preset.second }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = JarvisTextSecondary.copy(alpha = 0.2f))

                    Text("Voice Access Settings", color = JarvisTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require Verbs", color = JarvisTextPrimary, fontSize = 13.sp)
                            Text("Strictly require click/open/type/zoom for actions", color = JarvisTextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = requireVerbs,
                            onCheckedChange = { requireVerbs = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisPrimary,
                                checkedTrackColor = JarvisPrimary.copy(alpha = 0.5f),
                                uncheckedThumbColor = JarvisTextSecondary,
                                uncheckedTrackColor = JarvisBackground
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Standby Timeout", color = JarvisTextPrimary, fontSize = 13.sp)
                            Text("Sleep after inactivity duration", color = JarvisTextSecondary, fontSize = 10.sp)
                        }
                        var expandedTimeout by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expandedTimeout = true }) {
                                Text("${inactivityTimeoutMs / 1000}s", color = JarvisPrimary, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(
                                expanded = expandedTimeout,
                                onDismissRequest = { expandedTimeout = false },
                                modifier = Modifier.background(JarvisCardBg)
                            ) {
                                listOf(30000L, 40000L, 60000L).forEach { duration ->
                                    DropdownMenuItem(
                                        text = { Text("${duration / 1000} seconds", color = JarvisTextPrimary) },
                                        onClick = {
                                            inactivityTimeoutMs = duration
                                            expandedTimeout = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val phonePermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        incomingCallWake = isGranted
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Activate for Incoming Calls", color = JarvisTextPrimary, fontSize = 13.sp)
                            Text("Wake assistant automatically when ringing", color = JarvisTextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = incomingCallWake,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                } else {
                                    incomingCallWake = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisPrimary,
                                checkedTrackColor = JarvisPrimary.copy(alpha = 0.5f),
                                uncheckedThumbColor = JarvisTextSecondary,
                                uncheckedTrackColor = JarvisBackground
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active during calls", color = JarvisTextPrimary, fontSize = 13.sp)
                            Text("Keep listening even during phone calls", color = JarvisTextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = activeDuringCalls,
                            onCheckedChange = { activeDuringCalls = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisPrimary,
                                checkedTrackColor = JarvisPrimary.copy(alpha = 0.5f),
                                uncheckedThumbColor = JarvisTextSecondary,
                                uncheckedTrackColor = JarvisBackground
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Recognize Common Icons", color = JarvisTextPrimary, fontSize = 13.sp)
                            Text("Enable icon synonym mappings (send, search, mic)", color = JarvisTextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = recognizeCommonIcons,
                            onCheckedChange = { recognizeCommonIcons = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisPrimary,
                                checkedTrackColor = JarvisPrimary.copy(alpha = 0.5f),
                                uncheckedThumbColor = JarvisTextSecondary,
                                uncheckedTrackColor = JarvisBackground
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Configure Activation Key", color = JarvisTextPrimary, fontSize = 13.sp)
                            Text("Use physical Volume Down key to toggle assistant", color = JarvisTextSecondary, fontSize = 10.sp)
                        }
                        Switch(
                            checked = activationKeyEnabled,
                            onCheckedChange = { activationKeyEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = JarvisPrimary,
                                checkedTrackColor = JarvisPrimary.copy(alpha = 0.5f),
                                uncheckedThumbColor = JarvisTextSecondary,
                                uncheckedTrackColor = JarvisBackground
                            )
                        )
                    }

                    Button(
                        onClick = {
                            showSettingsDialog = false
                            onItemClick(CommandsReference)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisBackground),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, JarvisPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("VOICE COMMANDS REFERENCE GUIDE", color = JarvisPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        JarvisEngine.updateThemeSettings(context, selectedThemeMode, selectedFontColor, selectedIconColor)
                        JarvisEngine.updateAmbientLightColor(context, selectedAmbientColor)
                        
                        prefs.edit().apply {
                            putBoolean("require_verbs", requireVerbs)
                            putLong("inactivity_timeout_ms", inactivityTimeoutMs)
                            putBoolean("incoming_call_wake", incomingCallWake)
                            putBoolean("active_during_calls", activeDuringCalls)
                            putBoolean("recognize_common_icons", recognizeCommonIcons)
                            putBoolean("activation_key_enabled", activationKeyEnabled)
                        }.apply()

                        JarvisEngine.updateVoiceAccessSettings(
                            requireVerbs,
                            inactivityTimeoutMs,
                            incomingCallWake,
                            activeDuringCalls,
                            recognizeCommonIcons,
                            activationKeyEnabled
                        )

                        showSettingsDialog = false
                        Toast.makeText(context, "Configurations synchronized, sir.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("SAVE", color = JarvisPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("CANCEL", color = JarvisTextSecondary)
                }
            },
            containerColor = JarvisCardBg
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTrainingLayout(
    paddingValues: PaddingValues,
    assistantName: String,
    userName: String,
    hasPermissions: Boolean,
    calibrationStep: Int,
    calibrationLog: String,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    permissions: Array<String>,
    isSpeechListening: Boolean,
    speechListener: JarvisSpeechListener,
    onStartCalibration: () -> Unit,
    onCancelCalibration: () -> Unit,
    onFinishCalibration: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High-tech header
        Text(
            text = "ACOUSTIC VOICE ENROLLMENT",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = JarvisPrimary,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Futuristic Graphic / Lock icon / Pulsing Mic
        Box(
            modifier = Modifier
                .size(140.dp)
                .drawBehind {
                    drawCircle(
                        color = JarvisPrimary.copy(alpha = 0.08f),
                        radius = size.minDimension / 2,
                        style = Stroke(width = 2.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_training")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale_training"
            )
            Box(
                modifier = Modifier
                    .size(90.dp * if (isSpeechListening) scale else 1.0f)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isSpeechListening) JarvisAccent.copy(alpha = 0.4f) else JarvisPrimary.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(2.dp, if (isSpeechListening) JarvisAccent else JarvisPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSpeechListening) "🎙️" else "🔒",
                    fontSize = 32.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Voice Profile Training Required",
            fontWeight = FontWeight.Bold,
            color = JarvisTextPrimary,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To isolate your assistant from television chatter, surrounding speech, or other people nearby, voice registration is mandatory before dashboard access is permitted.",
            color = JarvisTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisPrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (calibrationStep == 0) {
                    Text(
                        text = "Training Prerequisites",
                        fontWeight = FontWeight.Bold,
                        color = JarvisTextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Speak clearly at a normal conversation distance.\n• Keep background environment quiet.\n• Verify permissions are fully granted.",
                        color = JarvisTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            if (!hasPermissions) {
                                launcher.launch(permissions)
                            } else {
                                onStartCalibration()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "START TRAINING WIZARD",
                            fontWeight = FontWeight.Bold,
                            color = JarvisBackground
                        )
                    }
                } else {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (calibrationStep) {
                                    1 -> "PHRASE 1 of 3"
                                    2 -> "PHRASE 2 of 3"
                                    3 -> "PHRASE 3 of 3"
                                    else -> "TRAINING COMPLETE"
                                },
                                fontWeight = FontWeight.Bold,
                                color = JarvisPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${((calibrationStep - 1) / 3f * 100).toInt()}%",
                                color = JarvisPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (calibrationStep - 1) / 3f },
                            color = JarvisAccent,
                            trackColor = JarvisPrimary.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "PLEASE SAY:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (calibrationStep) {
                                1 -> "\"Hello $assistantName\""
                                2 -> "\"$assistantName, read my screen\""
                                3 -> "\"$assistantName, go back\""
                                else -> "All phrases matched successfully!"
                            },
                            color = JarvisPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Acoustic Log:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = JarvisTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = calibrationLog,
                            color = JarvisTerminalText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JarvisBackground)
                                .border(1.dp, JarvisPrimary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        if (calibrationStep == 4) {
                            Button(
                                onClick = onFinishCalibration,
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisAccent),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "PROCEED TO DASHBOARD",
                                    color = JarvisBackground,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = onCancelCalibration,
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(JarvisError, JarvisError))
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("CANCEL TRAINING", color = JarvisError, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
