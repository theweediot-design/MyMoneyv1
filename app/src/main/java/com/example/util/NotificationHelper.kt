package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TransactionEntity
import java.text.NumberFormat
import java.util.Locale

object NotificationHelper {

    const val CHANNEL_ID = "financial_transactions"
    private const val CHANNEL_NAME = "Financial Transactions"
    private const val CHANNEL_DESC = "Real-time alerts for incoming bank credits and debits"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showTransactionNotification(context: Context, tx: TransactionEntity) {
        createNotificationChannel(context)

        val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
        val formattedAmount = inrFormat.format(tx.amount)

        val isCredit = tx.type.equals("CREDIT", ignoreCase = true)
        val title = if (isCredit) "Money Credited! 💰" else "Money Debited! 💸"

        val bankLabel = if (tx.bankName.isNotBlank() && tx.bankName != "Other Bank") tx.bankName else tx.bankCode
        val accSuffix = if (tx.accountNumberLast4.isNotBlank()) " (••••${tx.accountNumberLast4})" else ""

        val actionWord = if (isCredit) "credited to" else "debited from"
        val body = "$formattedAmount $actionWord $bankLabel$accSuffix"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            tx.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(tx.id.toInt().takeIf { it != 0 } ?: System.currentTimeMillis().toInt(), notification)
        } catch (_: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}
