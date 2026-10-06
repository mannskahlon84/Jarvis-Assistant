package com.example.jarvis_personalassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class IncomingCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            Log.d("IncomingCallReceiver", "Phone state changed: $state")
            
            val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            val incomingCallWake = prefs.getBoolean("incoming_call_wake", false)
            
            if (incomingCallWake && state == TelephonyManager.EXTRA_STATE_RINGING) {
                Log.d("IncomingCallReceiver", "Ringing state detected. Starting voice listener...")
                val listener = JarvisSpeechListener.getInstance(context.applicationContext)
                listener.startListening()
            }
        }
    }
}
