package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AccountEntity
import com.example.data.model.BankEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountName = :accountName ORDER BY timestamp DESC")
    fun getTransactionsByAccount(accountName: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE bankCode = :bankCode ORDER BY timestamp DESC")
    fun getTransactionsByBank(bankCode: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE amount = :amount AND timestamp BETWEEN :startWindow AND :endWindow AND bankCode = :bankCode LIMIT 5")
    suspend fun findPotentialTimeDuplicates(amount: Double, startWindow: Long, endWindow: Long, bankCode: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE (refNumber != '' AND refNumber = :refNumber) OR (utrNumber != '' AND utrNumber = :utrNumber) LIMIT 1")
    suspend fun findByRefOrUtr(refNumber: String, utrNumber: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE rawSmsBody != '' AND rawSmsBody = :rawBody LIMIT 1")
    suspend fun findByRawBody(rawBody: String): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int

    @Query("SELECT * FROM transactions WHERE isFlaggedForReview = 1 ORDER BY timestamp DESC")
    fun getFlaggedTransactions(): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET accountNumberLast4 = '', accountName = bankCode || ' - Unknown Account' WHERE accountNumberLast4 = '0000'")
    suspend fun cleanFakeAccountTransactions()

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Update
    suspend fun updateAll(transactions: List<TransactionEntity>)
}

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY bankCode ASC, accountName ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY bankCode ASC, accountName ASC")
    suspend fun getAccountsList(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE bankCode = :bankCode AND accountNumberLast4 = :last4 LIMIT 1")
    suspend fun findAccount(bankCode: String, last4: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE bankCode = :bankCode AND accountNumberLast4 != '' ORDER BY id ASC")
    suspend fun findAccountsByBank(bankCode: String): List<AccountEntity>

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM accounts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM accounts WHERE accountNumberLast4 = '0000'")
    suspend fun deleteFakeAccounts()

    @Query("DELETE FROM accounts WHERE accountNumberLast4 = '' OR accountName LIKE '%Unknown%'")
    suspend fun deleteUnknownAccounts()

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getCount(): Int
}

@Dao
interface BankDao {

    @Query("SELECT * FROM banks ORDER BY name ASC")
    fun getAllBanks(): Flow<List<BankEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(banks: List<BankEntity>)

    @Query("SELECT COUNT(*) FROM banks")
    suspend fun getCount(): Int
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int
}
