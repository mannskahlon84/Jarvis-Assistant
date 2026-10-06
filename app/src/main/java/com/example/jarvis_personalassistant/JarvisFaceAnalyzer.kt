package com.example.jarvis_personalassistant

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class JarvisFaceAnalyzer : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private var lastGestureTime = 0L
    private val GESTURE_COOLDOWN_MS = 2500L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastGestureTime > GESTURE_COOLDOWN_MS) {
                        for (face in faces) {
                            val smileProb = face.smilingProbability ?: 0f
                            val leftEyeOpen = face.leftEyeOpenProbability ?: 1f
                            val rightEyeOpen = face.rightEyeOpenProbability ?: 1f
                            val headEulerAngleY = face.headEulerAngleY
                            val headEulerAngleZ = face.headEulerAngleZ

                            var detectedGesture: String? = null

                            when {
                                leftEyeOpen < 0.15f && rightEyeOpen < 0.15f && smileProb < 0.50f -> {
                                    detectedGesture = "Blinking"
                                }
                                leftEyeOpen < 0.15f && rightEyeOpen > 0.80f -> {
                                    detectedGesture = "Winking"
                                }
                                rightEyeOpen < 0.15f && leftEyeOpen > 0.80f -> {
                                    detectedGesture = "Winking"
                                }
                                smileProb > 0.80f -> {
                                    detectedGesture = "Smiling"
                                }
                                headEulerAngleY < -25f -> {
                                    detectedGesture = "Head Turn Left"
                                }
                                headEulerAngleY > 25f -> {
                                    detectedGesture = "Head Turn Right"
                                }
                                headEulerAngleZ < -20f -> {
                                    detectedGesture = "Head Tilt Left"
                                }
                                headEulerAngleZ > 20f -> {
                                    detectedGesture = "Head Tilt Right"
                                }
                            }

                            if (detectedGesture != null) {
                                JarvisEngine.updateFaceExpression(detectedGesture)
                                lastGestureTime = currentTime
                                break
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("JarvisFaceAnalyzer", "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
