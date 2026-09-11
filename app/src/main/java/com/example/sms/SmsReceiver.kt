package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNotEmpty()) {
                val fullBody = StringBuilder()
                var sender = ""
                var timestamp = System.currentTimeMillis()

                for (msg in messages) {
                    fullBody.append(msg.messageBody)
                    sender = msg.originatingAddress ?: sender
                    timestamp = msg.timestampMillis
                }

                val bodyStr = fullBody.toString()
                if (bodyStr.isNotBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val manager = SmsDetectionManager.getInstance(context)
                            manager.processIncomingSms(bodyStr, sender, timestamp)
                        } catch (_: Exception) {
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
