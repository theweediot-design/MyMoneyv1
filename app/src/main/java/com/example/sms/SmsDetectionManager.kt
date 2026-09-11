package com.example.sms

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveChecklistState(
    val step1Detected: Boolean = true,
    val step2Parsed: Boolean = true,
    val step3Identified: Boolean = true,
    val step4Saved: Boolean = true,
    val step5Added: Boolean = true,
    val statusMessage: String = "Transaction added!",
    val lastTransaction: TransactionEntity? = null
)

class SmsDetectionManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val db = AppDatabase.getDatabase(context)
    private val dedupEngine = DeduplicationEngine(db.transactionDao(), db.accountDao())

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
        delay(250)

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
        delay(250)

        // Step 3: Identifying bank & account
        _checklistState.value = _checklistState.value.copy(
            step3Identified = true,
            statusMessage = "Identified ${parsed.bankName} (••••${parsed.accountNumberLast4})"
        )
        delay(250)

        // Step 4: Saving to database
        val result = dedupEngine.process(parsed)
        _checklistState.value = _checklistState.value.copy(
            step4Saved = true,
            statusMessage = "Saved to encrypted local Room database"
        )
        delay(200)

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

        return result
    }

    fun simulateTestSms(scenario: Int = 0) {
        scope.launch {
            val samples = listOf(
                // Scenario 0: UPI Food
                Pair(
                    "VK-HDFCBK",
                    "HDFC Bank: Rs 1,850.00 debited from a/c **9876 on 11-09-26 to SWIGGY UPI Ref 6291048291. Avl bal: Rs 27,050.00."
                ),
                // Scenario 1: Salary
                Pair(
                    "BZ-SBIINB",
                    "Dear SBI User, A/C ...1234 credited by Rs 32,000.00 on 11Sep26 by transfer from TECH LABS SALARY. Ref No SAL83921. Avl Bal Rs 80,250.00."
                ),
                // Scenario 2: Shopping
                Pair(
                    "AX-ICICIB",
                    "ICICI Bank Credit Card ending 6789 charged INR 4,999.00 at FLIPKART on 11-Sep-26. Avl limit INR 2,05,000.00."
                ),
                // Scenario 3: Duplicate UPI test (same ref number to verify dedup!)
                Pair(
                    "VK-HDFCBK",
                    "HDFC Bank: Your UPI payment of Rs 1,850.00 to SWIGGY was SUCCESSFUL. UPI Ref 6291048291."
                )
            )

            val chosen = samples[scenario % samples.size]
            processIncomingSms(chosen.second, chosen.first)
        }
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
