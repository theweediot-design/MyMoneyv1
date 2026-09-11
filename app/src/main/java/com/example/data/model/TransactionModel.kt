package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType {
    CREDIT,
    DEBIT
}

enum class PaymentMethod(val displayName: String) {
    UPI("UPI"),
    NEFT("NEFT"),
    RTGS("RTGS"),
    IMPS("IMPS"),
    ATM("ATM"),
    POS("POS"),
    CARD("Card"),
    SALARY("Salary"),
    REFUND("Refund"),
    CASHBACK("Cashback"),
    OTHER("Other")
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["amount", "timestamp", "bankCode"]),
        Index(value = ["refNumber"]),
        Index(value = ["utrNumber"]),
        Index(value = ["timestamp"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: String, // "CREDIT" or "DEBIT"
    val bankCode: String, // "SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "IDFC", "OTHERS"
    val bankName: String,
    val accountId: Long = 0,
    val accountName: String, // "SBI - Salary", "HDFC - Credit Card"
    val accountNumberLast4: String, // "1234"
    val paymentMethod: String, // "UPI", "NEFT", "ATM", etc.
    val merchant: String, // "Google Pay", "Swiggy", "Amazon", etc.
    val refNumber: String = "",
    val utrNumber: String = "",
    val timestamp: Long,
    val categoryName: String, // "Food & Dining", "Shopping", etc.
    val categoryColorHex: String = "#3B82F6",
    val confidenceScore: Float = 1.0f,
    val isFlaggedForReview: Boolean = false,
    val rawSmsBody: String = "",
    val notes: String = ""
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bankCode: String,
    val bankName: String,
    val accountName: String,
    val accountNumberLast4: String,
    val accountType: String = "SAVINGS", // "SALARY", "SAVINGS", "CURRENT", "CREDIT_CARD"
    val isActive: Boolean = true
)

@Entity(tableName = "banks")
data class BankEntity(
    @PrimaryKey
    val code: String,
    val name: String,
    val colorHex: String
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val colorHex: String,
    val iconName: String
)
