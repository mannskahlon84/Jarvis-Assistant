package com.example.jarvis_personalassistant

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var apiKey: String = ""
    private const val DEFAULT_API_KEY = ""

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        apiKey = savedKey
    }

    fun setApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("gemini_api_key", key).apply()
        apiKey = if (key.isNotBlank()) key else DEFAULT_API_KEY
    }

    fun getApiKey(): String = apiKey

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            val user = JarvisEngine.userName.value
            val assistant = JarvisEngine.assistantName.value
            return@withContext "$user, please set your Gemini API key in the $assistant Settings dashboard to enable advanced intelligence."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
        // System instruction to act as Jarvis
        val user = JarvisEngine.userName.value
        val assistant = JarvisEngine.assistantName.value
        val systemInstruction = "You are $assistant, a highly sophisticated, loyal, and witty AI personal assistant. Respond concisely, address the user as '$user', and keep responses brief (under 2 sentences) so they are easy to read aloud."

        val jsonBody = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemInstruction\n\nUser request: $prompt")
                        })
                    })
                })
            }
            put("contents", contentsArray)
            
            val toolsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("googleSearch", JSONObject())
                })
            }
            put("tools", toolsArray)
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        var attempt = 0
        val maxAttempts = 3
        var backoffMs = 500L

        while (attempt < maxAttempts) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: return@withContext "I received an empty response, sir."
                        val jsonResponse = JSONObject(bodyString)
                        val candidates = jsonResponse.getJSONArray("candidates")
                        if (candidates.length() > 0) {
                            val parts = candidates.getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                            if (parts.length() > 0) {
                                return@withContext parts.getJSONObject(0).getString("text").trim()
                            }
                        }
                        return@withContext "I couldn't parse the reply, sir."
                    } else {
                        val errBody = response.body?.string() ?: ""
                        Log.e(TAG, "Gemini API Error (Attempt ${attempt + 1}): Code ${response.code}, Body $errBody")
                        JarvisEngine.log("Gemini Error ${response.code} (Attempt ${attempt + 1})")
                        
                        if (response.code == 503 || response.code == 429) {
                            attempt++
                            if (attempt < maxAttempts) {
                                Thread.sleep(backoffMs)
                                backoffMs *= 2
                                continue
                            }
                        }
                        if (response.code == 429) {
                            if (apiKey == DEFAULT_API_KEY) {
                                return@withContext "The default API key is rate-limited, sir. Please set your own Gemini API key in settings."
                            } else {
                                return@withContext "My rate limit has been exceeded, sir. Please check your API key quota or wait a moment."
                            }
                        }
                        return@withContext "I encountered a communication error with the core intelligence mainframe, sir. Error code: ${response.code}."
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e(TAG, "Network exception in Gemini Client (Attempt ${attempt + 1})", e)
                JarvisEngine.log("Network Exception: ${e.message}")
                attempt++
                if (attempt < maxAttempts) {
                    Thread.sleep(backoffMs)
                    backoffMs *= 2
                    continue
                }
                return@withContext "Connection timed out. Mainframe is unreachable."
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception in Gemini Client", e)
                return@withContext "An unexpected exception occurred, sir."
            }
        }
        "I encountered an error connecting to the mainframe."
    }
}
