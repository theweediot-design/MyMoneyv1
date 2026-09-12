package com.example.util

import com.example.data.model.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TransactionDateGroup(
    val headerTitle: String,
    val dateKey: Long,
    val transactions: List<TransactionEntity>
)

object DateGroupingUtils {

    fun formatGroupHeader(timestamp: Long): String {
        val now = Calendar.getInstance()
        val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameDay = now.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR)

        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterdayCal.get(Calendar.YEAR) == txCal.get(Calendar.YEAR) &&
                yesterdayCal.get(Calendar.DAY_OF_YEAR) == txCal.get(Calendar.DAY_OF_YEAR)

        val fullDateStr = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(timestamp))

        return when {
            isSameDay -> "Today - $fullDateStr"
            isYesterday -> "Yesterday - $fullDateStr"
            else -> fullDateStr
        }
    }

    fun groupTransactions(transactions: List<TransactionEntity>): List<TransactionDateGroup> {
        val sorted = transactions.sortedByDescending { it.timestamp }

        val groups = LinkedHashMap<String, MutableList<TransactionEntity>>()
        val dateKeys = LinkedHashMap<String, Long>()

        val dayCal = Calendar.getInstance()
        for (tx in sorted) {
            val header = formatGroupHeader(tx.timestamp)
            if (!groups.containsKey(header)) {
                groups[header] = mutableListOf()
                dayCal.timeInMillis = tx.timestamp
                dayCal.set(Calendar.HOUR_OF_DAY, 0)
                dayCal.set(Calendar.MINUTE, 0)
                dayCal.set(Calendar.SECOND, 0)
                dayCal.set(Calendar.MILLISECOND, 0)
                dateKeys[header] = dayCal.timeInMillis
            }
            groups[header]?.add(tx)
        }

        return groups.map { (header, list) ->
            TransactionDateGroup(
                headerTitle = header,
                dateKey = dateKeys[header] ?: 0L,
                transactions = list
            )
        }
    }
}
