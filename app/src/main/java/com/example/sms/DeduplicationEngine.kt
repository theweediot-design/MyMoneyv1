package com.example.sms

import com.example.data.local.AccountDao
import com.example.data.local.TransactionDao
import com.example.data.model.AccountEntity
import com.example.data.model.TransactionEntity
import java.util.Locale

sealed class DeduplicationResult {
    data class Created(val transaction: TransactionEntity) : DeduplicationResult()
    data class Merged(val transaction: TransactionEntity, val reason: String) : DeduplicationResult()
    data class SkippedDuplicate(val existing: TransactionEntity) : DeduplicationResult()
}

class DeduplicationEngine(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) {

    companion object {
        // 15-minute sliding deduplication window
        private const val DEDUP_WINDOW_MS = 15 * 60 * 1000L
    }

    suspend fun process(parsed: ParsedSms): DeduplicationResult {
        if (!parsed.isValidTransaction) {
            throw IllegalArgumentException("Cannot process non-transaction SMS")
        }

        // 0. Stage 0: Exact duplicate SMS body already processed (prevents duplicates on inbox re-scans)
        if (parsed.rawBody.isNotBlank()) {
            val existingByBody = transactionDao.findByRawBody(parsed.rawBody)
            if (existingByBody != null) {
                return DeduplicationResult.SkippedDuplicate(existingByBody)
            }
        }

        // 1. Stage 1: Exact match via Reference Number or UTR Number from same bank
        if (parsed.refNumber.isNotBlank() || parsed.utrNumber.isNotBlank()) {
            val existingByRef = transactionDao.findByRefOrUtr(parsed.refNumber, parsed.utrNumber)
            if (existingByRef != null && existingByRef.bankCode.equals(parsed.bankCode, ignoreCase = true)) {
                // Merge complementary information into existing record
                val updated = existingByRef.copy(
                    refNumber = if (existingByRef.refNumber.isBlank()) parsed.refNumber else existingByRef.refNumber,
                    utrNumber = if (existingByRef.utrNumber.isBlank()) parsed.utrNumber else existingByRef.utrNumber,
                    merchant = if (isGenericMerchant(existingByRef.merchant) && !isGenericMerchant(parsed.merchant)) parsed.merchant else existingByRef.merchant,
                    accountNumberLast4 = if (existingByRef.accountNumberLast4.isBlank()) parsed.accountNumberLast4 else existingByRef.accountNumberLast4,
                    confidenceScore = maxOf(existingByRef.confidenceScore, parsed.confidenceScore)
                )
                transactionDao.update(updated)
                return DeduplicationResult.Merged(updated, "Matched via Reference/UTR Number: ${parsed.refNumber.ifBlank { parsed.utrNumber }}")
            }
        }

        // 2. Stage 2: Check time window duplicate with same amount and bank
        val startWindow = parsed.timestamp - DEDUP_WINDOW_MS
        val endWindow = parsed.timestamp + DEDUP_WINDOW_MS
        val timeCandidates = transactionDao.findPotentialTimeDuplicates(
            amount = parsed.amount,
            startWindow = startWindow,
            endWindow = endWindow,
            bankCode = parsed.bankCode
        )

        for (candidate in timeCandidates) {
            val sameType = candidate.type == parsed.type.name
            if (!sameType) continue

            // If reference numbers or UTRs are both present and differ, they are distinct transactions
            val refConflict = (candidate.refNumber.isNotBlank() && parsed.refNumber.isNotBlank() && candidate.refNumber != parsed.refNumber) ||
                    (candidate.utrNumber.isNotBlank() && parsed.utrNumber.isNotBlank() && candidate.utrNumber != parsed.utrNumber)
            if (refConflict) continue

            // Account check: both match, or one is unknown/blank. Distinct known accounts must never merge
            val accountCompatible = candidate.accountNumberLast4 == parsed.accountNumberLast4 ||
                    candidate.accountNumberLast4.isBlank() ||
                    parsed.accountNumberLast4.isBlank()
            if (!accountCompatible) continue

            // Merchant / Alert check:
            val sameMerchant = candidate.merchant.equals(parsed.merchant, ignoreCase = true)
            val oneIsGeneric = isGenericMerchant(candidate.merchant) || isGenericMerchant(parsed.merchant)
            val isAlertUpdate = isAlertOrUpdate(candidate.rawSmsBody, parsed.rawBody)

            // Never merge distinct genuine transactions of same amount unless merchants align or it's an alert/update
            if (sameMerchant || oneIsGeneric || isAlertUpdate) {
                val merged = candidate.copy(
                    accountNumberLast4 = if (candidate.accountNumberLast4.isBlank()) parsed.accountNumberLast4 else candidate.accountNumberLast4,
                    refNumber = if (candidate.refNumber.isBlank()) parsed.refNumber else candidate.refNumber,
                    utrNumber = if (candidate.utrNumber.isBlank()) parsed.utrNumber else candidate.utrNumber,
                    merchant = if (isGenericMerchant(candidate.merchant) && !isGenericMerchant(parsed.merchant)) parsed.merchant else candidate.merchant,
                    confidenceScore = maxOf(candidate.confidenceScore, parsed.confidenceScore)
                )
                transactionDao.update(merged)
                return DeduplicationResult.Merged(merged, "Merged bank notification within 15-minute window")
            }
        }

        // 3. Stage 3: Distinct transaction - resolve or create account
        val cleanLast4 = parsed.accountNumberLast4.trim()
        val existingAccount = accountDao.findAccount(parsed.bankCode, cleanLast4)
        val (accountId, accountName) = if (existingAccount != null) {
            existingAccount.id to existingAccount.accountName
        } else {
            val formattedName = if (cleanLast4.isNotBlank()) {
                "${parsed.bankCode} - Account ••••$cleanLast4"
            } else {
                "${parsed.bankCode} - Unknown Account"
            }
            val newAcc = AccountEntity(
                bankCode = parsed.bankCode,
                bankName = parsed.bankName,
                accountName = formattedName,
                accountNumberLast4 = cleanLast4,
                accountType = if (parsed.categoryName.equals("Salary", ignoreCase = true)) "SALARY" else "SAVINGS",
                isActive = true
            )
            val newId = accountDao.insert(newAcc)
            newId to formattedName
        }

        val newTransaction = TransactionEntity(
            amount = parsed.amount,
            type = parsed.type.name,
            bankCode = parsed.bankCode,
            bankName = parsed.bankName,
            accountId = accountId,
            accountName = accountName,
            accountNumberLast4 = cleanLast4,
            paymentMethod = parsed.paymentMethod.displayName,
            merchant = parsed.merchant,
            refNumber = parsed.refNumber,
            utrNumber = parsed.utrNumber,
            timestamp = parsed.timestamp,
            categoryName = parsed.categoryName,
            categoryColorHex = parsed.categoryColorHex,
            confidenceScore = parsed.confidenceScore,
            isFlaggedForReview = parsed.isFlaggedForReview,
            rawSmsBody = parsed.rawBody
        )

        val insertedId = transactionDao.insert(newTransaction)
        return DeduplicationResult.Created(newTransaction.copy(id = insertedId))
    }

    private fun isGenericMerchant(merchant: String): Boolean {
        val lower = merchant.lowercase(Locale.getDefault()).trim()
        return lower.isBlank() ||
                lower == "bank debit" ||
                lower == "direct credit" ||
                lower == "debit" ||
                lower == "credit" ||
                lower == "bank transaction" ||
                lower == "unknown merchant" ||
                lower == "account debit" ||
                lower == "account credit" ||
                lower == "bank transfer"
    }

    private fun isAlertOrUpdate(body1: String, body2: String): Boolean {
        val b1Lower = body1.lowercase(Locale.getDefault())
        val b2Lower = body2.lowercase(Locale.getDefault())
        return (b1Lower.contains("alert") || b2Lower.contains("alert") ||
                b1Lower.contains("update") || b2Lower.contains("update") ||
                b1Lower.contains("successful") || b2Lower.contains("successful"))
    }
}
