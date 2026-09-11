package com.example.sms

import com.example.data.local.AccountDao
import com.example.data.local.TransactionDao
import com.example.data.model.AccountEntity
import com.example.data.model.TransactionEntity

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

        // 1. Check direct match via Reference Number or UTR Number
        if (parsed.refNumber.isNotBlank() || parsed.utrNumber.isNotBlank()) {
            val existingByRef = transactionDao.findByRefOrUtr(parsed.refNumber, parsed.utrNumber)
            if (existingByRef != null) {
                // Merge better information into existing record
                val updated = existingByRef.copy(
                    refNumber = if (existingByRef.refNumber.isBlank()) parsed.refNumber else existingByRef.refNumber,
                    utrNumber = if (existingByRef.utrNumber.isBlank()) parsed.utrNumber else existingByRef.utrNumber,
                    merchant = if (existingByRef.merchant.startsWith("Bank Debit") || existingByRef.merchant.startsWith("Direct Credit")) parsed.merchant else existingByRef.merchant,
                    confidenceScore = maxOf(existingByRef.confidenceScore, parsed.confidenceScore)
                )
                transactionDao.update(updated)
                return DeduplicationResult.Merged(updated, "Matched via Reference/UTR Number: ${parsed.refNumber.ifBlank { parsed.utrNumber }}")
            }
        }

        // 2. Check time window duplicate with same amount and bank
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
            val accountCompatible = candidate.accountNumberLast4 == parsed.accountNumberLast4 ||
                    candidate.accountNumberLast4 == "0000" ||
                    parsed.accountNumberLast4 == "0000"

            if (sameType && accountCompatible) {
                // Merge multiple SMS notifications for the single real-world event
                val merged = candidate.copy(
                    accountNumberLast4 = if (candidate.accountNumberLast4 == "0000") parsed.accountNumberLast4 else candidate.accountNumberLast4,
                    refNumber = if (candidate.refNumber.isBlank()) parsed.refNumber else candidate.refNumber,
                    utrNumber = if (candidate.utrNumber.isBlank()) parsed.utrNumber else candidate.utrNumber,
                    confidenceScore = maxOf(candidate.confidenceScore, parsed.confidenceScore)
                )
                transactionDao.update(merged)
                return DeduplicationResult.Merged(merged, "Merged multiple bank/app notifications within 15-minute window")
            }
        }

        // 3. Not a duplicate: Genuinely distinct transaction
        // Resolve or create account
        val existingAccount = accountDao.findAccount(parsed.bankCode, parsed.accountNumberLast4)
        val (accountId, accountName) = if (existingAccount != null) {
            existingAccount.id to existingAccount.accountName
        } else {
            val formattedName = "${parsed.bankCode} - Account ••••${parsed.accountNumberLast4}"
            val newAcc = AccountEntity(
                bankCode = parsed.bankCode,
                bankName = parsed.bankName,
                accountName = formattedName,
                accountNumberLast4 = parsed.accountNumberLast4,
                accountType = if (parsed.categoryName == "Salary") "SALARY" else "SAVINGS",
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
            accountNumberLast4 = parsed.accountNumberLast4,
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
}
