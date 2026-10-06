package com.example.jarvis_personalassistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import com.konovalov.vad.silero.VadSilero
import com.konovalov.vad.silero.config.SampleRate
import com.konovalov.vad.silero.config.FrameSize
import com.konovalov.vad.silero.config.Mode

class JarvisSpeechListener private constructor(private val context: Context) : RecognitionListener {
    companion object {
        private const val TAG = "JarvisSpeech"
        val isListening = MutableStateFlow(false)
        private var sInstance: JarvisSpeechListener? = null
        private var isPausedTemp = false

        fun getInstance(context: Context): JarvisSpeechListener {
            if (sInstance == null) {
                sInstance = JarvisSpeechListener(context.applicationContext)
            }
            return sInstance!!
        }

        fun getInstance(): JarvisSpeechListener? = sInstance

        fun pauseTemporarily() {
            val instance = sInstance ?: return
            if (isListening.value) {
                isPausedTemp = true
                isListening.value = false
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        instance.speechRecognizer?.cancel()
                        instance.speechRecognizer?.destroy()
                        instance.speechRecognizer = null
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cancelling and destroying speech recognizer", e)
                    }
                }
                JarvisEngine.updateSpeechStatus("Speech paused")
            }
        }

        fun resumeTemporarily() {
            val instance = sInstance ?: return
            if (isPausedTemp) {
                isPausedTemp = false
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    instance.startListening()
                }
            }
        }

        private val collectedPitches = mutableListOf<Float>()

        fun clearCollectedPitches() {
            collectedPitches.clear()
        }

        fun getAverageCollectedPitch(): Float {
            val valid = collectedPitches.filter { it in 80f..300f }
            return if (valid.isNotEmpty()) valid.average().toFloat() else 0f
        }

        fun getAverageUtteranceEmbedding(): FloatArray? {
            val instance = sInstance ?: return null
            synchronized(instance.utteranceMelVectors) {
                if (instance.utteranceMelVectors.isEmpty()) return null
                return VoiceBiometrics.averageEmbeddings(instance.utteranceMelVectors)
            }
        }
    }


    private var speechRecognizer: SpeechRecognizer? = null

    private var vad: VadSilero? = null
    private val melFilterbank = VoiceBiometrics.MelFilterbank(sampleRate = 16000, fftSize = 512, numFilters = 20)
    private val audioBufferAccumulator = java.io.ByteArrayOutputStream()
    private var totalFrames = 0
    private var speechFrames = 0
    private val utteranceMelVectors = mutableListOf<FloatArray>()

    init {
        try {
            vad = VadSilero(
                context,
                sampleRate = SampleRate.SAMPLE_RATE_16K,
                frameSize = FrameSize.FRAME_SIZE_512,
                mode = Mode.AGGRESSIVE
            )
            Log.d(TAG, "VadSilero initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VadSilero", e)
        }
    }

    fun close() {
        try {
            vad?.close()
            vad = null
            Log.d(TAG, "VadSilero closed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close VadSilero", e)
        }
    }

    private fun detectPitch(pcmData: ByteArray, sampleRate: Int = 16000): Float {
        val shorts = ShortArray(pcmData.size / 2)
        for (i in shorts.indices) {
            val low = pcmData[2 * i].toInt() and 0xFF
            val high = pcmData[2 * i + 1].toInt()
            shorts[i] = ((high shl 8) or low).toShort()
        }

        val n = shorts.size
        if (n < 256) return -1f

        val r = FloatArray(n)
        for (lag in 0 until n / 2) {
            var sum = 0f
            for (i in 0 until n / 2) {
                sum += shorts[i].toFloat() * shorts[i + lag].toFloat()
            }
            r[lag] = sum
        }

        val minLag = sampleRate / 300 // ~53
        val maxLag = sampleRate / 80  // ~200

        if (maxLag >= n / 2) return -1f

        var peakLag = -1
        var peakVal = -1f

        for (lag in minLag..maxLag) {
            if (r[lag] > r[lag - 1] && r[lag] > r[lag + 1]) {
                if (peakLag == -1 || r[lag] > peakVal) {
                    peakLag = lag
                    peakVal = r[lag]
                }
            }
        }

        if (peakLag != -1 && peakVal > 0f) {
            return sampleRate.toFloat() / peakLag
        }

        return -1f
    }

    private fun getRecognizerIntent(): Intent {
        val langCode = JarvisEngine.selectedLanguage.value
        val langTag = when (langCode) {
            "en" -> "en-US"
            "ar" -> "ar-AE"
            "de" -> "de-DE"
            "es" -> "es-ES"
            "hi" -> "hi-IN"
            "pa" -> "pa-IN"
            "ur" -> "ur-PK"
            "ml" -> "ml-IN"
            "ta" -> "ta-IN"
            "bn" -> "bn-BD"
            "ne" -> "ne-NP"
            else -> "en-US"
        }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, langTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun startListening() {
        if (vad == null) {
            try {
                vad = VadSilero(
                    context,
                    sampleRate = SampleRate.SAMPLE_RATE_16K,
                    frameSize = FrameSize.FRAME_SIZE_512,
                    mode = Mode.AGGRESSIVE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reinitialize VadSilero in startListening", e)
            }
        }
        try {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer?.destroy()
                } catch (e: Exception) {}
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@JarvisSpeechListener)
            }
            speechRecognizer?.startListening(getRecognizerIntent())
            isListening.value = true
            JarvisEngine.updateSpeechStatus("Listening...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            JarvisEngine.updateSpeechStatus("Initialization failed")
            isListening.value = false
        }
    }

    fun stopListening() {
        isListening.value = false
        isPausedTemp = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        JarvisEngine.updateSpeechStatus("Stopped")
    }

    private var maxRmsDuringSpeech = 0f

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Ready for speech")
        maxRmsDuringSpeech = 0f
        clearCollectedPitches()
        audioBufferAccumulator.reset()
        totalFrames = 0
        speechFrames = 0
        synchronized(utteranceMelVectors) {
            utteranceMelVectors.clear()
        }
        JarvisEngine.updateSpeechStatus("Speak now")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Speech beginning")
        maxRmsDuringSpeech = 0f
        clearCollectedPitches()
        audioBufferAccumulator.reset()
        totalFrames = 0
        speechFrames = 0
        synchronized(utteranceMelVectors) {
            utteranceMelVectors.clear()
        }
        if (JarvisEngine.isSpeaking()) {
            JarvisEngine.stopSpeaking()
        }
        JarvisEngine.updateSpeechStatus("Listening...")
    }

    override fun onRmsChanged(rmsdB: Float) {
        if (rmsdB > maxRmsDuringSpeech) {
            maxRmsDuringSpeech = rmsdB
        }
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        if (buffer == null || buffer.isEmpty()) return

        val pitch = detectPitch(buffer)
        if (pitch in 80f..300f) {
            collectedPitches.add(pitch)
        }

        try {
            audioBufferAccumulator.write(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to audioBufferAccumulator", e)
            return
        }

        var accumulatedBytes = audioBufferAccumulator.toByteArray()
        var offset = 0
        val chunkSize = 1024

        while (accumulatedBytes.size - offset >= chunkSize) {
            val chunk = accumulatedBytes.copyOfRange(offset, offset + chunkSize)
            offset += chunkSize

            val shorts = ShortArray(512)
            for (i in 0 until 512) {
                val low = chunk[2 * i].toInt() and 0xFF
                val high = chunk[2 * i + 1].toInt()
                shorts[i] = ((high shl 8) or low).toShort()
            }

            totalFrames++
            val isSpeech = vad?.isSpeech(chunk) ?: false
            if (isSpeech) {
                speechFrames++
                try {
                    val windowed = VoiceBiometrics.applyHammingWindow(shorts)
                    val re = DoubleArray(512) { windowed[it].toDouble() }
                    val im = DoubleArray(512)
                    VoiceBiometrics.fft(re, im)
                    val powerSpectrum = FloatArray(257)
                    for (k in 0..256) {
                        powerSpectrum[k] = (re[k] * re[k] + im[k] * im[k]).toFloat()
                    }
                    val melVector = melFilterbank.compute(powerSpectrum)
                    synchronized(utteranceMelVectors) {
                        utteranceMelVectors.add(melVector)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error computing Mel spectrum in onBufferReceived", e)
                }
            }
        }

        audioBufferAccumulator.reset()
        if (accumulatedBytes.size > offset) {
            try {
                audioBufferAccumulator.write(accumulatedBytes, offset, accumulatedBytes.size - offset)
            } catch (e: Exception) {
                Log.e(TAG, "Error rewriting remaining bytes to audioBufferAccumulator", e)
            }
        }
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "Speech ended")
        JarvisEngine.updateSpeechStatus("Processing...")
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission missing"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Unknown error"
        }
        Log.e(TAG, "Speech Error: $errorMessage")
        
        // Auto-restart if we are still active, except if permission is missing
        if (isListening.value && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            JarvisEngine.updateSpeechStatus("Retrying...")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isListening.value) startListening()
            }, 1000)
        } else {
            isListening.value = false
            JarvisEngine.updateSpeechStatus("Stopped: $errorMessage")
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val command = matches[0]
            Log.d(TAG, "Speech result max RMS: $maxRmsDuringSpeech")
            if (maxRmsDuringSpeech > 0f && maxRmsDuringSpeech < 3.5f) {
                Log.d(TAG, "Speech ignored because max RMS ($maxRmsDuringSpeech) was below threshold (3.5)")
                JarvisEngine.log("Ignored distant audio (RMS: $maxRmsDuringSpeech)")
                if (isListening.value) {
                    startListening()
                }
                return
            }

            // Verify if pitch matches target user profile (if we received audio frames)
            val targetPitch = JarvisEngine.voicePitchTarget.value
            if (totalFrames > 0 && targetPitch > 0f) {
                val averagePitch = getAverageCollectedPitch()
                if (averagePitch > 0f) {
                    val percentageDiff = Math.abs(averagePitch - targetPitch) / targetPitch
                    if (percentageDiff > 0.25f) {
                        Log.d(TAG, "Speaker ignored: average pitch was $averagePitch Hz, target is $targetPitch Hz (diff: ${percentageDiff * 100}%)")
                        JarvisEngine.log("Ignored unmatched voice (Vocal Pitch: ${averagePitch.toInt()}Hz)")
                        if (isListening.value) {
                            startListening()
                        }
                        return
                    }
                }
            }

            // Verify if VAD ratio is sufficient (>0.8) (if we received audio frames)
            if (totalFrames > 0) {
                val speechRatio = speechFrames.toFloat() / totalFrames.toFloat()
                if (speechRatio < 0.8f) {
                    Log.d(TAG, "Speech ignored: VAD speech ratio $speechRatio is below 0.8")
                    JarvisEngine.log("Ignored background noise (VAD ratio: ${(speechRatio * 100).toInt()}%)")
                    if (isListening.value) {
                        startListening()
                    }
                    return
                }
            } else {
                Log.d(TAG, "VAD bypassed: no audio buffers received from speech service")
            }

            // Verify if voice embedding matches target profile (Cosine Similarity > 0.94) (if we received audio frames)
            val targetEmbedding = JarvisEngine.voiceEmbeddingTarget.value
            if (totalFrames > 0 && targetEmbedding != null && targetEmbedding.any { it != 0f }) {
                val incomingEmbedding = getAverageUtteranceEmbedding()
                if (incomingEmbedding == null) {
                    Log.d(TAG, "Speech ignored: no voice embedding extracted")
                    JarvisEngine.log("Ignored unmatched speaker (No voice embedding)")
                    if (isListening.value) {
                        startListening()
                    }
                    return
                }
                val similarity = VoiceBiometrics.cosineSimilarity(incomingEmbedding, targetEmbedding)
                if (similarity < 0.94f) {
                    Log.d(TAG, "Speaker ignored: Cosine Similarity was $similarity, target threshold is 0.94")
                    JarvisEngine.log("Ignored unmatched speaker (Voice Similarity: ${(similarity * 100).toInt()}%)")
                    if (isListening.value) {
                        startListening()
                    }
                    return
                }
                Log.d(TAG, "Speaker matched! Similarity: $similarity")
            } else if (totalFrames == 0 && targetEmbedding != null && targetEmbedding.any { it != 0f }) {
                Log.d(TAG, "Speaker verification bypassed: no audio buffers received from speech service")
            }

            JarvisEngine.processCommand(context, command)
        }
        // Auto-restart for continuous assistant voice flow
        if (isListening.value) {
            startListening()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            if (JarvisEngine.isConversing.value) {
                JarvisEngine.resetInactivityTimer(context)
            }
            JarvisEngine.updateSpeechStatus("Hearing: ${matches[0]}")
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
