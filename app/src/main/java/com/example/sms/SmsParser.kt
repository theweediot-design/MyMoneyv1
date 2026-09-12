package com.example.sms

import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionType
import java.util.Locale
import java.util.regex.Pattern

enum class ClassificationStatus {
    TRANSACTION,
    NEEDS_REVIEW,
    IGNORED
}

data class ParsedSms(
    val status: ClassificationStatus,
    val isValidTransaction: Boolean, // true ONLY if status == ClassificationStatus.TRANSACTION
    val amount: Double = 0.0,
    val type: TransactionType = TransactionType.DEBIT,
    val bankCode: String = "OTHERS",
    val bankName: String = "Others",
    val accountNumberLast4: String = "", // Empty string if unknown, NEVER fake 0000
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val merchant: String = "",
    val upiId: String = "",
    val refNumber: String = "",
    val utrNumber: String = "",
    val categoryName: String = "Others",
    val categoryColorHex: String = "#94A3B8",
    val confidenceScore: Float = 0.0f,
    val isFlaggedForReview: Boolean = false,
    val rawBody: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object SmsParser {

    // 1. Strict OTP & Security patterns: Any match makes the SMS immediately IGNORED
    private val OTP_PATTERNS = listOf(
        "otp", "one time password", "one-time password", "verification code",
        "secret code", "security code", "is your code", "is your secret code",
        "do not share", "never share", "use code to login", "valid for",
        "password reset", "authorization code", "login code", "auth code"
    )

    // 2. Strict Marketing, Loan, Ad & Promotional patterns
    private val PROMO_PATTERNS = listOf(
        "congratulations", "win up to", "discount", "pre-approved",
        "apply now", "loan offer", "flat 50%", "sale ends", "hurry",
        "exclusive offer", "reward points", "voucher", "deal of the",
        "upgrade your", "interest rate starting", "click here to claim",
        "scratch card", "get up to", "avail now", "invite friends"
    )

    // 3. Failed, Request, Due or Mandate patterns (not completed transactions)
    private val NON_TRANSACTION_PATTERNS = listOf(
        "due date", "bill generated", "minimum amount due", "due on",
        "payment request", "requested money", "mandate approved",
        "mandate created", "autopay scheduled", "declined", "transaction failed",
        "unsuccessful", "failed due to", "insufficient balance", "will be debited on",
        "scheduled for", "reminder:"
    )

    // Balance-only indicator patterns
    private val BALANCE_ONLY_PATTERNS = listOf(
        "current balance is", "account balance is", "clear balance is",
        "available balance is", "avl bal is", "avl balance is",
        "balance in a/c is", "balance in your account is", "ledger balance is"
    )

    // Debit action keywords
    private val DEBIT_KEYWORDS = listOf(
        "debited", "debit", "spent", "paid to", "sent to", "transferred to",
        "withdrawn", "purchase", "charged", "deducted", "dr.", " dr "
    )

    // Credit action keywords
    private val CREDIT_KEYWORDS = listOf(
        "credited", "credit", "received", "received from", "received in", "deposited", "salary",
        "refund", "cashback", "reversed", "cr.", " cr "
    )

    // Amount regex matching amounts with Rs, INR or ₹ symbols
    private val AMOUNT_PATTERN = Pattern.compile(
        """(?:(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?))|(?:([\d,]+(?:\.\d{1,2})?)\s*(?:rs|inr|₹))""",
        Pattern.CASE_INSENSITIVE
    )

    // Account last 3 or 4 digits
    private val ACCOUNT_PATTERN = Pattern.compile(
        """(?:\ba/?c\b|acct|account|card|ending|xx)\s*(?:no\.?|num)?\s*[:\s#]*[xX*.]*(\d{3,4})""",
        Pattern.CASE_INSENSITIVE
    )

    // Reference and UTR numbers
    private val REF_PATTERN = Pattern.compile(
        """(?:ref(?:\s*no\.?|\s*num)?|rrn|txn\s*(?:id|no\.?)|upi\s*ref)\s*[:\s#-]*([A-Za-z0-9]{6,22})""",
        Pattern.CASE_INSENSITIVE
    )

    private val UTR_PATTERN = Pattern.compile(
        """(?:utr\s*(?:no\.?)?)\s*[:\s#-]*([A-Za-z0-9]{8,22})""",
        Pattern.CASE_INSENSITIVE
    )

    // UPI VPA / ID regex
    private val UPI_ID_PATTERN = Pattern.compile(
        """([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)"""
    )

    // Merchant / Payee extraction
    private val MERCHANT_TO_PATTERN = Pattern.compile(
        """(?:to|at|vpa|info|paid to|transfer to)\s+([A-Za-z0-9\s._@&-]{2,30})""",
        Pattern.CASE_INSENSITIVE
    )

    private val MERCHANT_FROM_PATTERN = Pattern.compile(
        """(?:from|by transfer from|received from)\s+([A-Za-z0-9\s._@&-]{2,30})""",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(body: String, sender: String = "", smsTimestamp: Long = System.currentTimeMillis()): ParsedSms {
        val lowerBody = body.lowercase(Locale.ROOT)
        val lowerSender = sender.lowercase(Locale.ROOT)

        // Rule 1: OTP or Security verification -> STRICTLY IGNORED
        if (OTP_PATTERNS.any { lowerBody.contains(it) }) {
            return ParsedSms(
                status = ClassificationStatus.IGNORED,
                isValidTransaction = false,
                rawBody = body,
                timestamp = smsTimestamp
            )
        }

        // Rule 2: Promotional / Advertisement / Pre-approved -> STRICTLY IGNORED
        if (PROMO_PATTERNS.any { lowerBody.contains(it) }) {
            return ParsedSms(
                status = ClassificationStatus.IGNORED,
                isValidTransaction = false,
                rawBody = body,
                timestamp = smsTimestamp
            )
        }

        // Rule 3: Payment requests, due alerts, mandates or failed transactions -> STRICTLY IGNORED
        if (NON_TRANSACTION_PATTERNS.any { lowerBody.contains(it) }) {
            return ParsedSms(
                status = ClassificationStatus.IGNORED,
                isValidTransaction = false,
                rawBody = body,
                timestamp = smsTimestamp
            )
        }

        // Rule 4: Balance-Only alerts (no financial debit/credit occurred) -> STRICTLY IGNORED
        val hasDebit = DEBIT_KEYWORDS.any { lowerBody.contains(it) }
        val hasCredit = CREDIT_KEYWORDS.any { lowerBody.contains(it) }

        if (!hasDebit && !hasCredit) {
            val isBalanceOnly = BALANCE_ONLY_PATTERNS.any { lowerBody.contains(it) } ||
                    (lowerBody.contains("bal") && !lowerBody.contains("debited") && !lowerBody.contains("credited"))
            if (isBalanceOnly) {
                return ParsedSms(
                    status = ClassificationStatus.IGNORED,
                    isValidTransaction = false,
                    rawBody = body,
                    timestamp = smsTimestamp
                )
            }
            // If neither debit nor credit action is found, classify as NEEDS_REVIEW without creating transaction
            return ParsedSms(
                status = ClassificationStatus.NEEDS_REVIEW,
                isValidTransaction = false,
                rawBody = body,
                timestamp = smsTimestamp
            )
        }

        // Rule 5: Extract Transaction Amount (exclude Avl Bal / Limit amounts)
        val parsedAmount = extractTransactionAmount(body)
        if (parsedAmount == null || parsedAmount <= 0.0) {
            return ParsedSms(
                status = ClassificationStatus.NEEDS_REVIEW,
                isValidTransaction = false,
                rawBody = body,
                timestamp = smsTimestamp
            )
        }

        // Rule 6: Determine Debit vs Credit accurately
        val type = when {
            hasCredit && !hasDebit -> TransactionType.CREDIT
            hasDebit && !hasCredit -> TransactionType.DEBIT
            // If both words exist (e.g. "debited ... balance credited"), check precedence around the amount
            else -> determinePrecedence(lowerBody)
        }

        // Rule 7: Identify Bank reliably
        val (bankCode, bankName) = identifyBank(lowerSender, lowerBody)

        // Rule 8: Extract Account Last 4 (Strict: never fabricate "0000")
        val last4 = extractAccountLast4(body)

        // Rule 9: Payment Method
        val method = determinePaymentMethod(lowerBody)

        // Rule 10: Reference Number and UTR
        var refNumber = ""
        val refMatcher = REF_PATTERN.matcher(body)
        if (refMatcher.find()) {
            refNumber = refMatcher.group(1)?.trim() ?: ""
        }

        var utrNumber = ""
        val utrMatcher = UTR_PATTERN.matcher(body)
        if (utrMatcher.find()) {
            utrNumber = utrMatcher.group(1)?.trim() ?: ""
        }

        // Rule 11: UPI ID
        var upiId = ""
        val upiMatcher = UPI_ID_PATTERN.matcher(body)
        if (upiMatcher.find()) {
            upiId = upiMatcher.group(1)?.trim() ?: ""
        }

        // Rule 12: Merchant / Beneficiary
        var merchant = ""
        if (type == TransactionType.CREDIT) {
            val fromMatcher = MERCHANT_FROM_PATTERN.matcher(body)
            if (fromMatcher.find()) {
                merchant = cleanMerchant(fromMatcher.group(1) ?: "")
            }
        } else {
            val toMatcher = MERCHANT_TO_PATTERN.matcher(body)
            if (toMatcher.find()) {
                merchant = cleanMerchant(toMatcher.group(1) ?: "")
            }
        }

        if (merchant.isBlank() && upiId.isNotBlank()) {
            merchant = upiId
        }

        if (merchant.isBlank()) {
            merchant = when (method) {
                PaymentMethod.ATM -> if (bankCode != "OTHERS") "$bankCode ATM" else "ATM Withdrawal"
                PaymentMethod.SALARY -> "Salary Credit"
                PaymentMethod.UPI -> "UPI Payment"
                PaymentMethod.CARD -> "Card Transaction"
                PaymentMethod.NETBANKING -> "Net Banking"
                PaymentMethod.REFUND -> "Refund"
                PaymentMethod.CASHBACK -> "Cashback"
                else -> if (type == TransactionType.CREDIT) "Direct Credit" else "Bank Debit"
            }
        }

        // Rule 13: Categorization
        val (categoryName, categoryColor) = categorize(lowerBody, merchant.lowercase(Locale.ROOT), method, type)

        // Confidence calculation
        var confidence = 0.40f // Base confidence for meeting strict filters
        if (bankCode != "OTHERS") confidence += 0.20f
        if (last4.isNotEmpty()) confidence += 0.20f
        if (refNumber.isNotEmpty() || utrNumber.isNotEmpty()) confidence += 0.10f
        if (merchant.isNotEmpty() && !merchant.startsWith("Bank Debit") && !merchant.startsWith("Direct Credit")) confidence += 0.10f

        val isFlagged = confidence < 0.70f || (bankCode == "OTHERS" && last4.isEmpty())

        return ParsedSms(
            status = ClassificationStatus.TRANSACTION,
            isValidTransaction = true,
            amount = parsedAmount,
            type = type,
            bankCode = bankCode,
            bankName = bankName,
            accountNumberLast4 = last4, // Clean string or empty, NEVER fabricated
            paymentMethod = method,
            merchant = merchant,
            upiId = upiId,
            refNumber = refNumber,
            utrNumber = utrNumber,
            categoryName = categoryName,
            categoryColorHex = categoryColor,
            confidenceScore = confidence.coerceIn(0.0f, 1.0f),
            isFlaggedForReview = isFlagged,
            rawBody = body,
            timestamp = smsTimestamp
        )
    }

    private fun extractTransactionAmount(body: String): Double? {
        val matcher = AMOUNT_PATTERN.matcher(body)
        val candidateAmounts = mutableListOf<Pair<Int, Double>>()

        while (matcher.find()) {
            val amtStr = matcher.group(1) ?: matcher.group(2)
            if (amtStr != null) {
                val clean = amtStr.replace(",", "").trim()
                val d = clean.toDoubleOrNull()
                if (d != null && d > 0.0) {
                    candidateAmounts.add(matcher.start() to d)
                }
            }
        }

        if (candidateAmounts.isEmpty()) return null
        if (candidateAmounts.size == 1) return candidateAmounts.first().second

        // If multiple amounts are present, find the one that is NOT an "Avl Bal" or "Limit"
        val lower = body.lowercase(Locale.ROOT)
        for ((startIdx, amt) in candidateAmounts) {
            val prefix = lower.substring(0, startIdx).trim()
            val isBalance = prefix.endsWith("avl bal") || prefix.endsWith("available balance") ||
                    prefix.endsWith("bal") || prefix.endsWith("balance") ||
                    prefix.endsWith("limit") || prefix.endsWith("avl limit") ||
                    prefix.endsWith("bal:") || prefix.endsWith("bal.") ||
                    prefix.endsWith("balance:") || prefix.endsWith("balance.")
            if (!isBalance) {
                return amt
            }
        }

        // Fallback: Return the first candidate amount
        return candidateAmounts.first().second
    }

    private fun determinePrecedence(lowerBody: String): TransactionType {
        val debitIdx = lowerBody.indexOf("debited")
        val creditIdx = lowerBody.indexOf("credited")
        return when {
            debitIdx != -1 && (creditIdx == -1 || debitIdx < creditIdx) -> TransactionType.DEBIT
            creditIdx != -1 -> TransactionType.CREDIT
            lowerBody.contains("spent") || lowerBody.contains("paid") -> TransactionType.DEBIT
            lowerBody.contains("received") || lowerBody.contains("deposited") -> TransactionType.CREDIT
            else -> TransactionType.DEBIT
        }
    }

    private fun identifyBank(lowerSender: String, lowerBody: String): Pair<String, String> {
        return when {
            lowerSender.contains("sbi") || lowerSender.contains("sbin") || lowerBody.contains("state bank of india") || lowerBody.contains("sbi") ->
                "SBI" to "State Bank of India"
            lowerSender.contains("hdfc") || lowerBody.contains("hdfc") ->
                "HDFC" to "HDFC Bank"
            lowerSender.contains("icici") || lowerBody.contains("icici") ->
                "ICICI" to "ICICI Bank"
            lowerSender.contains("axis") || lowerSender.contains("utibr") || lowerBody.contains("axis") ->
                "AXIS" to "Axis Bank"
            lowerSender.contains("kotak") || lowerSender.contains("kmb") || lowerBody.contains("kotak") ->
                "KOTAK" to "Kotak Mahindra Bank"
            lowerSender.contains("idfc") || lowerSender.contains("idfcpb") || lowerBody.contains("idfc") ->
                "IDFC" to "IDFC FIRST Bank"
            lowerSender.contains("pnb") || lowerBody.contains("punjab national") ->
                "OTHERS" to "Punjab National Bank"
            lowerSender.contains("bob") || lowerBody.contains("bank of baroda") ->
                "OTHERS" to "Bank of Baroda"
            lowerSender.contains("canara") || lowerBody.contains("canara bank") ->
                "OTHERS" to "Canara Bank"
            lowerSender.contains("indus") || lowerBody.contains("indusind") ->
                "OTHERS" to "IndusInd Bank"
            lowerSender.contains("yes") || lowerBody.contains("yes bank") ->
                "OTHERS" to "Yes Bank"
            lowerSender.contains("fed") || lowerBody.contains("federal bank") ->
                "OTHERS" to "Federal Bank"
            lowerSender.contains("paytm") || lowerBody.contains("paytm bank") ->
                "OTHERS" to "Paytm Payments Bank"
            else -> "OTHERS" to "Other Bank"
        }
    }

    private fun extractAccountLast4(body: String): String {
        val matcher = ACCOUNT_PATTERN.matcher(body)
        if (matcher.find()) {
            val digits = matcher.group(1)
            if (digits != null && digits.length in 3..4) {
                return digits.padStart(4, '0')
            }
        }
        return ""
    }

    private fun determinePaymentMethod(lowerBody: String): PaymentMethod {
        return when {
            lowerBody.contains("salary") -> PaymentMethod.SALARY
            lowerBody.contains("upi") || lowerBody.contains("vpa") -> PaymentMethod.UPI
            lowerBody.contains("neft") -> PaymentMethod.NEFT
            lowerBody.contains("rtgs") -> PaymentMethod.RTGS
            lowerBody.contains("imps") -> PaymentMethod.IMPS
            lowerBody.contains("atm") || lowerBody.contains("withdrawn") -> PaymentMethod.ATM
            lowerBody.contains("pos") -> PaymentMethod.POS
            lowerBody.contains("ecs") -> PaymentMethod.ECS
            lowerBody.contains("nach") -> PaymentMethod.NACH
            lowerBody.contains("netbanking") || lowerBody.contains("net banking") || lowerBody.contains("internet banking") -> PaymentMethod.NETBANKING
            lowerBody.contains("card") || lowerBody.contains("credit card") || lowerBody.contains("debit card") || lowerBody.contains("ending") -> PaymentMethod.CARD
            lowerBody.contains("refund") -> PaymentMethod.REFUND
            lowerBody.contains("cashback") -> PaymentMethod.CASHBACK
            else -> PaymentMethod.OTHER
        }
    }

    private fun cleanMerchant(raw: String): String {
        var clean = raw.trim()
        val stopWords = listOf("on", "at", "by", "ref", "avl", "bal", "limit", "call", "not", "use", "date", "via", "for", "upi")
        for (sw in stopWords) {
            val idx = clean.indexOf(" $sw ", ignoreCase = true)
            if (idx > 0) {
                clean = clean.substring(0, idx).trim()
            }
        }
        val dotUpi = clean.indexOf(".upi", ignoreCase = true)
        if (dotUpi > 0) clean = clean.substring(0, dotUpi).trim()
        val dotRef = clean.indexOf(".ref", ignoreCase = true)
        if (dotRef > 0) clean = clean.substring(0, dotRef).trim()
        val trailingStopWords = listOf("upi", "ref", "imps", "neft", "rtgs", "pos")
        for (tsw in trailingStopWords) {
            if (clean.endsWith(" $tsw", ignoreCase = true)) {
                clean = clean.substring(0, clean.length - tsw.length - 1).trim()
            }
        }
        // Remove trailing punctuation or whitespace
        clean = clean.trimEnd('.', ',', '-', ';', ':', '/')
        return clean.take(28).trim()
    }

    private fun categorize(
        lowerBody: String,
        lowerMerchant: String,
        method: PaymentMethod,
        type: TransactionType
    ): Pair<String, String> {
        return when {
            method == PaymentMethod.SALARY || lowerBody.contains("salary") || lowerBody.contains("payroll") ->
                "Salary" to "#22C55E"

            method == PaymentMethod.ATM || lowerBody.contains("atm") ->
                "ATM" to "#EF4444"

            lowerBody.contains("swiggy") || lowerBody.contains("zomato") || lowerMerchant.contains("swiggy") ||
                    lowerMerchant.contains("zomato") || lowerBody.contains("restaurant") ||
                    lowerBody.contains("mcdonalds") || lowerBody.contains("dominos") ||
                    lowerBody.contains("starbucks") || lowerBody.contains("cafe") ||
                    lowerBody.contains("eats") || lowerBody.contains("dining") ->
                "Food & Dining" to "#F59E0B"

            lowerBody.contains("amazon") || lowerBody.contains("flipkart") || lowerBody.contains("myntra") ||
                    lowerMerchant.contains("amazon") || lowerMerchant.contains("flipkart") ||
                    lowerMerchant.contains("myntra") || lowerMerchant.contains("croma") ||
                    lowerBody.contains("retail") || lowerBody.contains("supermarket") || lowerBody.contains("mall") ->
                "Shopping" to "#EC4899"

            lowerBody.contains("airtel") || lowerBody.contains("jio") || lowerBody.contains("bescom") ||
                    lowerBody.contains("electricity") || lowerBody.contains("water bill") ||
                    lowerBody.contains("gas bill") || lowerBody.contains("broadband") ||
                    lowerBody.contains("recharge") ->
                "Bills & Recharge" to "#3B82F6"

            lowerBody.contains("uber") || lowerBody.contains("ola") || lowerBody.contains("irctc") ||
                    lowerBody.contains("flight") || lowerBody.contains("makemytrip") ||
                    lowerBody.contains("indigo") || lowerBody.contains("metro") ||
                    lowerBody.contains("redbus") ->
                "Travel" to "#06B6D4"

            lowerBody.contains("petrol") || lowerBody.contains("fuel") || lowerBody.contains("hpcl") ||
                    lowerBody.contains("bpcl") || lowerBody.contains("ioc") || lowerBody.contains("indian oil") ->
                "Fuel" to "#F97316"

            lowerBody.contains("apollo") || lowerBody.contains("pharmacy") || lowerBody.contains("hospital") ||
                    lowerBody.contains("1mg") || lowerBody.contains("medplus") || lowerBody.contains("clinic") ->
                "Healthcare" to "#10B981"

            lowerBody.contains("netflix") || lowerBody.contains("prime") || lowerBody.contains("hotstar") ||
                    lowerBody.contains("pvr") || lowerBody.contains("inox") ||
                    lowerBody.contains("bookmyshow") || lowerBody.contains("spotify") ->
                "Entertainment" to "#8B5CF6"

            lowerBody.contains("zerodha") || lowerBody.contains("groww") || lowerBody.contains("mutual fund") ||
                    lowerBody.contains("sip") || lowerBody.contains("dividend") ->
                "Investment" to "#14B8A6"

            lowerBody.contains("rent") || lowerBody.contains("nobroker") || lowerBody.contains("housing") ->
                "Rent" to "#A855F7"

            lowerBody.contains("emi") || lowerBody.contains("loan repayment") || method == PaymentMethod.ECS || method == PaymentMethod.NACH ->
                "EMI" to "#E11D48"

            lowerBody.contains("insurance") || lowerBody.contains("lic") ->
                "Insurance" to "#0EA5E9"

            lowerBody.contains("cashback") || method == PaymentMethod.CASHBACK ->
                "Cashback" to "#84CC16"

            lowerBody.contains("refund") || lowerBody.contains("reversal") || method == PaymentMethod.REFUND ->
                "Refund" to "#10B981"

            method == PaymentMethod.UPI || method == PaymentMethod.NEFT || method == PaymentMethod.IMPS || method == PaymentMethod.RTGS ->
                "Transfers" to "#64748B"

            type == TransactionType.CREDIT ->
                "Salary" to "#22C55E"

            else ->
                "Others" to "#94A3B8"
        }
    }
}
