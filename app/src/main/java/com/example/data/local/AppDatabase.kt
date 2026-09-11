package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AccountEntity
import com.example.data.model.BankEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        BankEntity::class,
        CategoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun bankDao(): BankDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mymoney_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val bankDao = database.bankDao()
            val categoryDao = database.categoryDao()

            if (bankDao.getCount() == 0) {
                bankDao.insertAll(
                    listOf(
                        BankEntity("SBI", "State Bank of India", "#2563EB"),
                        BankEntity("HDFC", "HDFC Bank", "#0284C7"),
                        BankEntity("ICICI", "ICICI Bank", "#EA580C"),
                        BankEntity("AXIS", "Axis Bank", "#BE185D"),
                        BankEntity("KOTAK", "Kotak Mahindra Bank", "#DC2626"),
                        BankEntity("IDFC", "IDFC FIRST Bank", "#9333EA"),
                        BankEntity("OTHERS", "Others", "#64748B")
                    )
                )
            }

            if (categoryDao.getCount() == 0) {
                categoryDao.insertAll(
                    listOf(
                        CategoryEntity("shopping", "Shopping", "#EC4899", "shopping_bag"),
                        CategoryEntity("food", "Food & Dining", "#F59E0B", "restaurant"),
                        CategoryEntity("bills", "Bills & Recharge", "#3B82F6", "receipt_long"),
                        CategoryEntity("travel", "Travel", "#06B6D4", "flight"),
                        CategoryEntity("fuel", "Fuel", "#F97316", "local_gas_station"),
                        CategoryEntity("healthcare", "Healthcare", "#10B981", "local_hospital"),
                        CategoryEntity("entertainment", "Entertainment", "#8B5CF6", "movie"),
                        CategoryEntity("education", "Education", "#6366F1", "school"),
                        CategoryEntity("salary", "Salary", "#22C55E", "payments"),
                        CategoryEntity("investment", "Investment", "#14B8A6", "trending_up"),
                        CategoryEntity("transfer", "Transfers", "#64748B", "sync_alt"),
                        CategoryEntity("atm", "ATM", "#EF4444", "local_atm"),
                        CategoryEntity("rent", "Rent", "#A855F7", "home"),
                        CategoryEntity("emi", "EMI", "#E11D48", "account_balance"),
                        CategoryEntity("insurance", "Insurance", "#0EA5E9", "security"),
                        CategoryEntity("refund", "Refund", "#10B981", "replay"),
                        CategoryEntity("cashback", "Cashback", "#84CC16", "redeem"),
                        CategoryEntity("other", "Others", "#94A3B8", "category")
                    )
                )
            }
        }
    }
}
