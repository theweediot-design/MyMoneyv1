package com.example.sms

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.TransactionEntity
import com.example.data.preferences.BankPreferenceManager
import com.example.util.NotificationHelper
import com.example.util.VoiceAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class LiveChecklistState(
    val step1Detected: Boolean = false,
    val step2Parsed: Boolean = false,
    val step3Identified: Boolean = false,
    val step4Saved: Boolean = false,
    val step5Added: Boolean = false,
    val statusMessage: String = "Waiting for incoming bank SMS...",
    val lastTransaction: TransactionEntity? = null
)

class SmsDetectionManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val db = AppDatabase.getDatabase(context)
    private val dedupEngine = DeduplicationEngine(db.transactionDao(), db.accountDao())
    private val bankPrefManager = BankPreferenceManager(context)

    private val _checklistState = MutableStateFlow(LiveChecklistState())
    val checklistState: StateFlow<LiveChecklistState> = _checklistState.asStateFlow()

    private val _isServiceActive = MutableStateFlow(true)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    fun toggleService(active: Boolean) {
        _isServiceActive.value = active
    }

    suspend fun processIncomingSms(body: String, sender: String, timestamp: Long = System.currentTimeMillis()): DeduplicationResult? {
        if (!_isServiceActive.value) return null

        // Step 1: New SMS detected
        _checklistState.value = LiveChecklistState(
            step1Detected = true,
            step2Parsed = false,
            step3Identified = false,
            step4Saved = false,
            step5Added = false,
            statusMessage = "New SMS detected from ${sender.ifBlank { "Bank" }}"
        )

        // Step 2: Parsing transaction...
        val parsed = SmsParser.parse(body, sender, timestamp)
        if (!parsed.isValidTransaction) {
            _checklistState.value = _checklistState.value.copy(
                step2Parsed = false,
                statusMessage = "Non-transaction SMS ignored"
            )
            return null
        }

        _checklistState.value = _checklistState.value.copy(
            step2Parsed = true,
            statusMessage = "Parsed ${parsed.type.name} of ₹${parsed.amount}"
        )

        // Step 3: Identifying bank & account
        val accDisplay = if (parsed.accountNumberLast4.isNotBlank()) "••••${parsed.accountNumberLast4}" else "Account"
        _checklistState.value = _checklistState.value.copy(
            step3Identified = true,
            statusMessage = "Identified ${parsed.bankName} ($accDisplay)"
        )

        // Step 4: Saving to database
        val result = dedupEngine.process(parsed)
        _checklistState.value = _checklistState.value.copy(
            step4Saved = true,
            statusMessage = "Saved to encrypted local Room database"
        )

        // Step 5: Transaction added / Merged
        val tx = when (result) {
            is DeduplicationResult.Created -> result.transaction
            is DeduplicationResult.Merged -> result.transaction
            is DeduplicationResult.SkippedDuplicate -> result.existing
        }

        val finalMsg = when (result) {
            is DeduplicationResult.Created -> "Transaction added successfully!"
            is DeduplicationResult.Merged -> "Merged with existing record (duplicate prevented)"
            is DeduplicationResult.SkippedDuplicate -> "Duplicate ignored"
        }

        _checklistState.value = LiveChecklistState(
            step1Detected = true,
            step2Parsed = true,
            step3Identified = true,
            step4Saved = true,
            step5Added = true,
            statusMessage = finalMsg,
            lastTransaction = tx
        )

        // Trigger Local Push Notification and Voice Alert for new or merged transaction
        if (result is DeduplicationResult.Created || result is DeduplicationResult.Merged) {
            try {
                NotificationHelper.showTransactionNotification(context, tx)
            } catch (_: Exception) {}

            try {
                val voiceAlertsEnabled = bankPrefManager.voiceAlertsEnabledFlow.first()
                if (voiceAlertsEnabled) {
                    val isCredit = tx.type.equals("CREDIT", ignoreCase = true)
                    VoiceAlertManager.playVoiceAlert(context, isCredit)
                }
            } catch (_: Exception) {}
        }

        return result
    }

    companion object {
        @Volatile
        private var INSTANCE: SmsDetectionManager? = null

        fun getInstance(context: Context): SmsDetectionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SmsDetectionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
