package com.example.jarvis_personalassistant.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.jarvis_personalassistant.JarvisEngine
import com.example.jarvis_personalassistant.JarvisFaceAnalyzer
import com.example.jarvis_personalassistant.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureMappingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE) }

    val isFaceActive by JarvisEngine.isFaceTrackingActive.collectAsStateWithLifecycle()
    val detectedFaceExpression by JarvisEngine.faceExpression.collectAsStateWithLifecycle()

    val permissions = arrayOf(Manifest.permission.CAMERA)
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasCameraPermission = results.values.all { it }
        if (!hasCameraPermission) {
            Toast.makeText(context, "Camera permission is required for face tracking.", Toast.LENGTH_SHORT).show()
        }
    }

    // Local state for each gesture configuration
    var smileAction by remember { mutableStateOf(prefs.getString("gesture_smile_action", "NONE") ?: "NONE") }
    var smileValue by remember { mutableStateOf(prefs.getString("gesture_smile_value", "") ?: "") }
    
    var winkAction by remember { mutableStateOf(prefs.getString("gesture_wink_action", "BACK") ?: "BACK") }
    var winkValue by remember { mutableStateOf(prefs.getString("gesture_wink_value", "") ?: "") }

    var blinkAction by remember { mutableStateOf(prefs.getString("gesture_blink_action", "NONE") ?: "NONE") }
    var blinkValue by remember { mutableStateOf(prefs.getString("gesture_blink_value", "") ?: "") }

    var turnLeftAction by remember { mutableStateOf(prefs.getString("gesture_turn_left_action", "NONE") ?: "NONE") }
    var turnLeftValue by remember { mutableStateOf(prefs.getString("gesture_turn_left_value", "") ?: "") }

    var turnRightAction by remember { mutableStateOf(prefs.getString("gesture_turn_right_action", "NONE") ?: "NONE") }
    var turnRightValue by remember { mutableStateOf(prefs.getString("gesture_turn_right_value", "") ?: "") }

    var tiltLeftAction by remember { mutableStateOf(prefs.getString("gesture_tilt_left_action", "NONE") ?: "NONE") }
    var tiltLeftValue by remember { mutableStateOf(prefs.getString("gesture_tilt_left_value", "") ?: "") }

    var tiltRightAction by remember { mutableStateOf(prefs.getString("gesture_tilt_right_action", "NONE") ?: "NONE") }
    var tiltRightValue by remember { mutableStateOf(prefs.getString("gesture_tilt_right_value", "") ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GESTURE CONFIGURATION CORE",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = JarvisPrimary,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text(
                            text = "◀",
                            color = JarvisPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JarvisBackground)
            )
        },
        containerColor = JarvisBackground
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Core Status Control Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, JarvisPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "GESTURE DETECTOR INSTANCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = JarvisTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isFaceActive) "ONLINE & RECORDING" else "STANDBY / SUSPENDED",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFaceActive) JarvisAccent else JarvisError,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Switch(
                            checked = isFaceActive,
                            onCheckedChange = { active ->
                                if (!hasCameraPermission) {
                                    launcher.launch(permissions)
                                } else {
                                    JarvisEngine.updateFaceTrackingActive(context, active)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = JarvisAccent, checkedTrackColor = JarvisAccent.copy(0.4f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Camera Preview Box for gesture validation
            AnimatedVisibility(visible = isFaceActive && hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, JarvisPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx -> PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
                        modifier = Modifier.fillMaxSize(),
                        update = { previewView ->
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().apply { surfaceProvider = previewView.surfaceProvider }
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build().apply {
                                        setAnalyzer(ContextCompat.getMainExecutor(context), JarvisFaceAnalyzer())
                                    }
                                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                                } catch (exc: Exception) {
                                    Log.e("GestureMappingsScreen", "Camera usecase binding failed", exc)
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "DETECTED: ${detectedFaceExpression.uppercase()}",
                            color = JarvisPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Header
            Text(
                text = "GESTURE INTERACTION CORES",
                color = JarvisTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Start
            )

            // 4. Config rows for each of the 7 gestures
            GestureConfigRow(
                gestureName = "Smiling",
                currentAction = smileAction,
                currentValue = smileValue,
                onActionChanged = { smileAction = it },
                onValueChanged = { smileValue = it }
            )

            GestureConfigRow(
                gestureName = "Winking",
                currentAction = winkAction,
                currentValue = winkValue,
                onActionChanged = { winkAction = it },
                onValueChanged = { winkValue = it }
            )

            GestureConfigRow(
                gestureName = "Blinking",
                currentAction = blinkAction,
                currentValue = blinkValue,
                onActionChanged = { blinkAction = it },
                onValueChanged = { blinkValue = it }
            )

            GestureConfigRow(
                gestureName = "Head Turn Left",
                currentAction = turnLeftAction,
                currentValue = turnLeftValue,
                onActionChanged = { turnLeftAction = it },
                onValueChanged = { turnLeftValue = it }
            )

            GestureConfigRow(
                gestureName = "Head Turn Right",
                currentAction = turnRightAction,
                currentValue = turnRightValue,
                onActionChanged = { turnRightAction = it },
                onValueChanged = { turnRightValue = it }
            )

            GestureConfigRow(
                gestureName = "Head Tilt Left",
                currentAction = tiltLeftAction,
                currentValue = tiltLeftValue,
                onActionChanged = { tiltLeftAction = it },
                onValueChanged = { tiltLeftValue = it }
            )

            GestureConfigRow(
                gestureName = "Head Tilt Right",
                currentAction = tiltRightAction,
                currentValue = tiltRightValue,
                onActionChanged = { tiltRightAction = it },
                onValueChanged = { tiltRightValue = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Save configurations button
            Button(
                onClick = {
                    prefs.edit().apply {
                        putString("gesture_smile_action", smileAction)
                        putString("gesture_smile_value", smileValue)

                        putString("gesture_wink_action", winkAction)
                        putString("gesture_wink_value", winkValue)

                        putString("gesture_blink_action", blinkAction)
                        putString("gesture_blink_value", blinkValue)

                        putString("gesture_turn_left_action", turnLeftAction)
                        putString("gesture_turn_left_value", turnLeftValue)

                        putString("gesture_turn_right_action", turnRightAction)
                        putString("gesture_turn_right_value", turnRightValue)

                        putString("gesture_tilt_left_action", tiltLeftAction)
                        putString("gesture_tilt_left_value", tiltLeftValue)

                        putString("gesture_tilt_right_action", tiltRightAction)
                        putString("gesture_tilt_right_value", tiltRightValue)
                    }.apply()

                    Toast.makeText(context, "Gesture interaction cores synchronized, sir.", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = JarvisSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "SAVE & SYNCHRONIZE CORES",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureConfigRow(
    gestureName: String,
    currentAction: String,
    currentValue: String,
    onActionChanged: (String) -> Unit,
    onValueChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val actions = listOf("NONE", "BACK", "HOME", "OPEN_APP", "TYPE_TEXT")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(JarvisCardBg, RoundedCornerShape(12.dp))
            .border(1.dp, JarvisPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = gestureName.uppercase(),
            color = JarvisPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Action Dropdown Selection
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(JarvisBackground, RoundedCornerShape(4.dp))
                .border(1.dp, JarvisTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .clickable { expanded = !expanded }
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currentAction, color = JarvisTextPrimary, fontSize = 13.sp)
                Text("▼", color = JarvisPrimary, fontSize = 10.sp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(JarvisCardBg)
            ) {
                actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action, color = JarvisTextPrimary) },
                        onClick = {
                            onActionChanged(action)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (currentAction == "OPEN_APP" || currentAction == "TYPE_TEXT") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChanged,
                label = {
                    Text(
                        text = if (currentAction == "OPEN_APP") "App Name to Open" else "Text to Type",
                        color = JarvisTextSecondary,
                        fontSize = 11.sp
                    )
                },
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
    }
}
