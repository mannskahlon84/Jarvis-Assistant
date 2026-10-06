package com.example.jarvis_personalassistant

import kotlin.math.*

object VoiceBiometrics {

    // Radix-2 FFT implementation
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var bits = 0
        while ((1 shl bits) < n) bits++
        for (i in 0 until n) {
            val j = java.lang.Integer.reverse(i) ushr (32 - bits)
            if (j > i) {
                val tempRe = re[i]; re[i] = re[j]; re[j] = tempRe
                val tempIm = im[i]; im[i] = im[j]; im[j] = tempIm
            }
        }
        var len = 2
        while (len <= n) {
            val ang = 2.0 * Math.PI / len
            val wlenRe = cos(ang)
            val wlenIm = -sin(ang)
            for (i in 0 until n step len) {
                var wRe = 1.0
                var wIm = 0.0
                for (j in 0 until len / 2) {
                    val uRe = re[i + j]
                    val uIm = im[i + j]
                    val targetIdx = i + j + len / 2
                    val tRe = re[targetIdx] * wRe - im[targetIdx] * wIm
                    val tIm = re[targetIdx] * wIm + im[targetIdx] * wRe
                    re[i + j] = uRe + tRe
                    im[i + j] = uIm + tIm
                    re[targetIdx] = uRe - tRe
                    im[targetIdx] = uIm - tIm
                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                }
            }
            len = len shl 1
        }
    }

    // Apply Hamming window to PCM samples
    fun applyHammingWindow(frame: ShortArray): FloatArray {
        val windowed = FloatArray(frame.size)
        val n = frame.size
        for (i in frame.indices) {
            val w = 0.54 - 0.46 * cos(2.0 * Math.PI * i / (n - 1))
            windowed[i] = (frame[i].toFloat() * w).toFloat()
        }
        return windowed
    }

    // Cosine similarity metric between two embedding vectors
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA <= 0f || normB <= 0f) return 0f
        return (dotProduct / (sqrt(normA.toDouble()) * sqrt(normB.toDouble()))).toFloat()
    }

    // Average multiple embeddings to create a single target profile
    fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        if (embeddings.isEmpty()) return FloatArray(20)
        val size = embeddings[0].size
        val avg = FloatArray(size)
        for (emb in embeddings) {
            for (i in 0 until size) {
                avg[i] += emb[i]
            }
        }
        for (i in 0 until size) {
            avg[i] /= embeddings.size
        }
        return avg
    }

    // Mel Scale Filterbank class
    class MelFilterbank(val sampleRate: Int = 16000, val fftSize: Int = 512, val numFilters: Int = 20) {
        private val filterWeights = Array(numFilters) { FloatArray(fftSize / 2 + 1) }

        init {
            val minFreq = 300.0
            val maxFreq = sampleRate / 2.0
            val minMel = freqToMel(minFreq)
            val maxMel = freqToMel(maxFreq)
            
            val melPoints = DoubleArray(numFilters + 2)
            for (i in melPoints.indices) {
                melPoints[i] = minMel + i * (maxMel - minMel) / (numFilters + 1)
            }
            
            val binIndices = IntArray(numFilters + 2)
            for (i in binIndices.indices) {
                val freq = melToFreq(melPoints[i])
                binIndices[i] = Math.round(freq * (fftSize + 1) / sampleRate).toInt()
            }
            
            for (m in 0 until numFilters) {
                val startBin = binIndices[m]
                val peakBin = binIndices[m + 1]
                val endBin = binIndices[m + 2]
                
                for (k in startBin until peakBin) {
                    if (k < filterWeights[m].size) {
                        filterWeights[m][k] = (k - startBin).toFloat() / (peakBin - startBin)
                    }
                }
                for (k in peakBin until endBin) {
                    if (k < filterWeights[m].size) {
                        filterWeights[m][k] = (endBin - k).toFloat() / (endBin - peakBin)
                    }
                }
            }
        }

        private fun freqToMel(freq: Double): Double = 2595.0 * log10(1.0 + freq / 700.0)
        private fun melToFreq(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        fun compute(powerSpectrum: FloatArray): FloatArray {
            val features = FloatArray(numFilters)
            for (m in 0 until numFilters) {
                var sum = 0f
                for (k in 0 until powerSpectrum.size) {
                    sum += powerSpectrum[k] * filterWeights[m][k]
                }
                features[m] = ln(max(sum.toDouble(), 1e-6)).toFloat()
            }
            return features
        }
    }
}
