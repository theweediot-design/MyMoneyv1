package com.example.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class ScanProgress(
    val isScanning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val addedTransactions: Int = 0,
    val mergedDuplicates: Int = 0
)

class SmsScanner(private val context: Context) {

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    suspend fun scanInbox(onBatchComplete: ((Int, Int) -> Unit)? = null): ScanProgress = withContext(Dispatchers.IO) {
        val db = AppDatabase.getDatabase(context)
        val dedupEngine = DeduplicationEngine(db.transactionDao(), db.accountDao())

        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        var totalFound = 0
        val cursor = try {
            context.contentResolver.query(uri, projection, null, null, "${Telephony.Sms.DATE} ASC")
        } catch (e: Exception) {
            null
        }

        if (cursor == null) {
            return@withContext ScanProgress(isScanning = false)
        }

        totalFound = cursor.count
        _scanProgress.value = ScanProgress(isScanning = true, current = 0, total = totalFound)

        var processed = 0
        var added = 0
        var merged = 0

        val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
        val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
        val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

        while (cursor.moveToNext()) {
            val address = if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else ""
            val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
            val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()

            if (body.isNotBlank()) {
                val parsed = SmsParser.parse(body, address, date)
                if (parsed.isValidTransaction) {
                    when (dedupEngine.process(parsed)) {
                        is DeduplicationResult.Created -> added++
                        is DeduplicationResult.Merged -> merged++
                        is DeduplicationResult.SkippedDuplicate -> merged++
                    }
                }
            }

            processed++
            if (processed % 50 == 0 || processed == totalFound) {
                _scanProgress.value = ScanProgress(
                    isScanning = true,
                    current = processed,
                    total = totalFound,
                    addedTransactions = added,
                    mergedDuplicates = merged
                )
                onBatchComplete?.invoke(processed, totalFound)
            }
        }
        cursor.close()

        val finalResult = ScanProgress(
            isScanning = false,
            current = processed,
            total = totalFound,
            addedTransactions = added,
            mergedDuplicates = merged
        )
        _scanProgress.value = finalResult
        finalResult
    }
}
