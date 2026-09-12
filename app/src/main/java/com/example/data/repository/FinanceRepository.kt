package com.example.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AccountEntity
import com.example.data.model.BankEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FinanceRepository(private val database: AppDatabase) {

    private val transactionDao = database.transactionDao()
    private val accountDao = database.accountDao()
    private val bankDao = database.bankDao()
    private val categoryDao = database.categoryDao()

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val allBanks: Flow<List<BankEntity>> = bankDao.getAllBanks()
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    suspend fun insertTransaction(transaction: TransactionEntity): Long = withContext(Dispatchers.IO) {
        transactionDao.insert(transaction)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        transactionDao.update(transaction)
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        transactionDao.deleteById(id)
    }

    suspend fun updateAccount(account: AccountEntity) = withContext(Dispatchers.IO) {
        accountDao.update(account)
    }

    suspend fun sanitizeDatabase() = withContext(Dispatchers.IO) {
        try {
            // 1. Purge unknown and fake accounts
            accountDao.deleteUnknownAccounts()
            accountDao.deleteFakeAccounts()
            transactionDao.cleanFakeAccountTransactions()

            // 2. Re-classify existing transactions currently labeled as "OTHERS" or missing bank
            val allTx = transactionDao.getAllTransactionsList()
            val updatedTxList = mutableListOf<TransactionEntity>()

            for (tx in allTx) {
                var modified = false
                var currentTx = tx

                if (currentTx.bankCode == "OTHERS" && currentTx.rawSmsBody.isNotBlank()) {
                    val (detectedCode, detectedName) = com.example.sms.SmsParser.identifyBank("", currentTx.rawSmsBody)
                    if (detectedCode != "OTHERS") {
                        currentTx = currentTx.copy(
                            bankCode = detectedCode,
                            bankName = detectedName
                        )
                        modified = true
                    }
                }

                // If account number is blank, re-inspect SMS body for genuine bank AC pattern
                if (currentTx.accountNumberLast4.isBlank() && currentTx.rawSmsBody.isNotBlank()) {
                    val genuineLast4 = com.example.sms.SmsParser.extractAccountLast4(currentTx.rawSmsBody)
                    if (genuineLast4.isNotBlank()) {
                        currentTx = currentTx.copy(accountNumberLast4 = genuineLast4)
                        modified = true
                    }
                }

                if (modified) {
                    updatedTxList.add(currentTx)
                }
            }

            if (updatedTxList.isNotEmpty()) {
                transactionDao.updateAll(updatedTxList)
            }

            // 3. Update account branding & fix "OTHERS" labels for genuine banks (e.g. Federal Bank)
            val refreshedTxList = transactionDao.getAllTransactionsList()
            val currentAccounts = accountDao.getAccountsList()
            val accountsToUpdate = mutableListOf<AccountEntity>()
            val txAccountUpdates = mutableListOf<TransactionEntity>()

            for (acc in currentAccounts) {
                val accTx = refreshedTxList.filter { it.accountId == acc.id }
                var targetBankCode = acc.bankCode
                var targetBankName = acc.bankName

                // If account is labeled OTHERS, check if its transactions belong to a genuine bank (e.g. FEDERAL)
                if (targetBankCode == "OTHERS") {
                    var foundCode: String? = null
                    for (t in accTx) {
                        if (t.bankCode != "OTHERS") {
                            foundCode = t.bankCode
                            break
                        }
                        val (c, _) = com.example.sms.SmsParser.identifyBank("", t.rawSmsBody)
                        if (c != "OTHERS") {
                            foundCode = c
                            break
                        }
                    }

                    if (foundCode != null) {
                        targetBankCode = foundCode
                        targetBankName = if (foundCode == "FEDERAL") "Federal Bank"
                        else com.example.sms.SmsParser.INDIAN_BANK_REGISTRY.find { it.code == foundCode }?.name ?: foundCode
                    }
                }

                val expectedAccountName = if (acc.accountNumberLast4.isNotBlank()) {
                    "$targetBankCode - Account ••••${acc.accountNumberLast4}"
                } else {
                    targetBankName
                }

                if (acc.bankCode != targetBankCode || acc.bankName != targetBankName || acc.accountName != expectedAccountName) {
                    val updatedAcc = acc.copy(
                        bankCode = targetBankCode,
                        bankName = targetBankName,
                        accountName = expectedAccountName
                    )
                    accountsToUpdate.add(updatedAcc)

                    for (tx in accTx) {
                        txAccountUpdates.add(
                            tx.copy(
                                bankCode = targetBankCode,
                                bankName = targetBankName,
                                accountName = expectedAccountName
                            )
                        )
                    }
                }
            }

            for (acc in accountsToUpdate) {
                accountDao.update(acc)
            }
            if (txAccountUpdates.isNotEmpty()) {
                transactionDao.updateAll(txAccountUpdates)
            }

            // 4. Resolve Cross-Bank Account Duplication & eliminate phantom beneficiary accounts
            // (e.g. Account 6325 mistakenly created under Kotak when it was a beneficiary of Kotak 3453)
            val refreshedAccounts = accountDao.getAccountsList()
            val accountsToDelete = mutableListOf<Long>()
            val txToReassign = mutableListOf<TransactionEntity>()
            val latestTxList = transactionDao.getAllTransactionsList()

            val accountsByBank = refreshedAccounts.groupBy { it.bankCode }

            // Specifically check Kotak: if Kotak has both 3453 and 6325, 6325 is a beneficiary of transfer
            val kotakAccounts = accountsByBank["KOTAK"] ?: emptyList()
            val kotakPrimary = kotakAccounts.find { it.accountNumberLast4 == "3453" }
                ?: kotakAccounts.firstOrNull { it.accountNumberLast4.isNotBlank() }

            if (kotakPrimary != null) {
                for (kAcc in kotakAccounts) {
                    if (kAcc.id == kotakPrimary.id) continue
                    if (kAcc.accountNumberLast4 == "6325") {
                        val kTx = latestTxList.filter { it.accountId == kAcc.id }
                        for (tx in kTx) {
                            txToReassign.add(
                                tx.copy(
                                    accountId = kotakPrimary.id,
                                    accountName = kotakPrimary.accountName,
                                    accountNumberLast4 = kotakPrimary.accountNumberLast4
                                )
                            )
                        }
                        accountsToDelete.add(kAcc.id)
                    }
                }
            }

            // 5. Merge remaining fragmented card/reference accounts into parent bank's primary account
            for ((_, bankAccList) in accountsByBank) {
                val validAccounts = bankAccList.filter {
                    it.accountNumberLast4.isNotBlank() &&
                            !it.accountName.contains("Unknown", ignoreCase = true) &&
                            !it.accountName.contains("Card", ignoreCase = true) &&
                            it.id !in accountsToDelete
                }

                val primaryAccount = validAccounts.firstOrNull()

                if (primaryAccount != null) {
                    for (acc in bankAccList) {
                        if (acc.id == primaryAccount.id || acc.id in accountsToDelete) continue

                        val accTx = latestTxList.filter { it.accountId == acc.id }
                        val isCardOrUnknown = acc.accountName.contains("Card", ignoreCase = true) ||
                                acc.accountName.contains("Unknown", ignoreCase = true) ||
                                acc.accountNumberLast4.isBlank() ||
                                accTx.all { tx ->
                                    val txType = if (tx.type == "CREDIT") com.example.data.model.TransactionType.CREDIT else com.example.data.model.TransactionType.DEBIT
                                    tx.paymentMethod == "Card" ||
                                            tx.rawSmsBody.contains("card ending", ignoreCase = true) ||
                                            tx.rawSmsBody.contains("••••", ignoreCase = true) ||
                                            com.example.sms.SmsParser.extractAccountLast4(tx.rawSmsBody, txType).isBlank()
                                }

                        if (isCardOrUnknown && accTx.isNotEmpty()) {
                            for (tx in accTx) {
                                txToReassign.add(
                                    tx.copy(
                                        accountId = primaryAccount.id,
                                        accountName = primaryAccount.accountName,
                                        accountNumberLast4 = primaryAccount.accountNumberLast4
                                    )
                                )
                            }
                            accountsToDelete.add(acc.id)
                        } else if (accTx.isEmpty() && (acc.accountNumberLast4.isBlank() || acc.accountName.contains("Unknown", ignoreCase = true))) {
                            accountsToDelete.add(acc.id)
                        }
                    }
                } else {
                    for (acc in bankAccList) {
                        if (acc.accountNumberLast4.isBlank() || acc.accountName.contains("Unknown", ignoreCase = true)) {
                            accountsToDelete.add(acc.id)
                        }
                    }
                }
            }

            if (txToReassign.isNotEmpty()) {
                transactionDao.updateAll(txToReassign)
            }
            if (accountsToDelete.isNotEmpty()) {
                accountDao.deleteByIds(accountsToDelete.distinct())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun exportTransactionsToCsv(context: Context, transactions: List<TransactionEntity>): Boolean = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val file = File(exportDir, "MyMoney_Transactions_${dateFormat.format(Date())}.csv")

            val readableDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            file.bufferedWriter().use { writer ->
                writer.write("ID,Date,Bank,Account,Type,Amount,Category,PaymentMethod,Merchant,RefNumber,UTR,Confidence\n")
                for (tx in transactions) {
                    val dateStr = readableDateFormat.format(Date(tx.timestamp))
                    val line = listOf(
                        tx.id.toString(),
                        "\"$dateStr\"",
                        "\"${tx.bankName}\"",
                        "\"${tx.accountName}\"",
                        tx.type,
                        tx.amount.toString(),
                        "\"${tx.categoryName}\"",
                        tx.paymentMethod,
                        "\"${tx.merchant.replace("\"", "\"\"")}\"",
                        "\"${tx.refNumber}\"",
                        "\"${tx.utrNumber}\"",
                        tx.confidenceScore.toString()
                    ).joinToString(",")
                    writer.write("$line\n")
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MyMoney Transactions Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Transactions CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
