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
