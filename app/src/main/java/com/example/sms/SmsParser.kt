package com.example.sms

import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionType
import java.util.Locale
import java.util.regex.Pattern

data class ParsedSms(
    val isValidTransaction: Boolean,
    val amount: Double = 0.0,
    val type: TransactionType = TransactionType.DEBIT,
    val bankCode: String = "OTHERS",
    val bankName: String = "Others",
    val accountNumberLast4: String = "0000",
    val paymentMethod: PaymentMethod = PaymentMethod.OTHER,
    val merchant: String = "",
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

    private val OTP_PATTERNS = listOf(
        "otp", "one time password", "verification code", "secret code",
        "do not share", "never share", "security code", "is your code"
    )

    private val PROMO_PATTERNS = listOf(
        "congratulations", "win up to", "discount", "pre-approved",
        "apply now", "loan offer", "flat 50%", "sale ends", "hurry"
    )

    private val AMOUNT_REGEX = Pattern.compile(
        """(?:(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?))|(?:([\d,]+(?:\.\d{1,2})?)\s*(?:rs|inr|₹))""",
        Pattern.CASE_INSENSITIVE
    )

    private val ACCOUNT_REGEX = Pattern.compile(
        """(?:a/c|acct|account|card|ending|xx)\s*(?:no\.?|num)?\s*[:\s#]*[xX*.]*(\d{3,4})""",
        Pattern.CASE_INSENSITIVE
    )

    private val REF_REGEX = Pattern.compile(
        """(?:ref(?:\s*no\.?|\s*num)?|utr|rrn|txn\s*(?:id|no\.?)|upi\s*ref)\s*[:\s#-]*([A-Za-z0-9]{6,22})""",
        Pattern.CASE_INSENSITIVE
    )

    private val UTR_REGEX = Pattern.compile(
        """(?:utr\s*(?:no\.?)?)\s*[:\s#-]*([A-Za-z0-9]{8,22})""",
        Pattern.CASE_INSENSITIVE
    )

    private val MERCHANT_TO_REGEX = Pattern.compile(
        """(?:to|at|vpa|info|transfer to)\s+([A-Za-z0-9\s._@&-]{3,30})""",
        Pattern.CASE_INSENSITIVE
    )

    private val MERCHANT_FROM_REGEX = Pattern.compile(
        """(?:from|by transfer from|received from)\s+([A-Za-z0-9\s._@&-]{3,30})""",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(body: String, sender: String = "", smsTimestamp: Long = System.currentTimeMillis()): ParsedSms {
        val lowerBody = body.lowercase(Locale.ROOT)
        val lowerSender = sender.lowercase(Locale.ROOT)

        // 1. Check if OTP or purely promotional
        val isOtp = OTP_PATTERNS.any { lowerBody.contains(it) }
        val hasTxKeywords = lowerBody.contains("debited") || lowerBody.contains("credited") ||
                lowerBody.contains("spent") || lowerBody.contains("paid") ||
                lowerBody.contains("withdrawn") || lowerBody.contains("transferred") ||
                lowerBody.contains("charged") || lowerBody.contains("refund") ||
                lowerBody.contains("salary") || lowerBody.contains("deposited")

        if (isOtp && !hasTxKeywords) {
            return ParsedSms(isValidTransaction = false, rawBody = body, timestamp = smsTimestamp)
        }

        if (!hasTxKeywords && PROMO_PATTERNS.any { lowerBody.contains(it) }) {
            return ParsedSms(isValidTransaction = false, rawBody = body, timestamp = smsTimestamp)
        }

        // 2. Extract Amount
        val amountMatcher = AMOUNT_REGEX.matcher(body)
        var parsedAmount = 0.0
        var foundAmount = false
        if (amountMatcher.find()) {
            val amtStr = amountMatcher.group(1) ?: amountMatcher.group(2)
            if (amtStr != null) {
                val clean = amtStr.replace(",", "").trim()
                parsedAmount = clean.toDoubleOrNull() ?: 0.0
                if (parsedAmount > 0.0) {
                    foundAmount = true
                }
            }
        }

        if (!foundAmount) {
            return ParsedSms(isValidTransaction = false, rawBody = body, timestamp = smsTimestamp)
        }

        // 3. Determine Debit vs Credit
        var type = TransactionType.DEBIT
        var typeFound = false
        if (lowerBody.contains("credited") || lowerBody.contains("credit") ||
            lowerBody.contains("received from") || lowerBody.contains("deposited") ||
            lowerBody.contains("salary") || lowerBody.contains("refund") ||
            lowerBody.contains("cashback") || lowerBody.contains("reversed") ||
            lowerBody.contains("cr.") || lowerBody.endsWith(" cr")
        ) {
            type = TransactionType.CREDIT
            typeFound = true
        } else if (lowerBody.contains("debited") || lowerBody.contains("debit") ||
            lowerBody.contains("spent") || lowerBody.contains("paid to") ||
            lowerBody.contains("withdrawn") || lowerBody.contains("purchase") ||
            lowerBody.contains("charged") || lowerBody.contains("deducted") ||
            lowerBody.contains("dr.") || lowerBody.endsWith(" dr")
        ) {
            type = TransactionType.DEBIT
            typeFound = true
        }

        // 4. Identify Bank
        var bankCode = "OTHERS"
        var bankName = "Others"

        when {
            lowerSender.contains("sbi") || lowerBody.contains("sbi") || lowerBody.contains("state bank") -> {
                bankCode = "SBI"
                bankName = "State Bank of India"
            }
            lowerSender.contains("hdfc") || lowerBody.contains("hdfc") -> {
                bankCode = "HDFC"
                bankName = "HDFC Bank"
            }
            lowerSender.contains("icici") || lowerBody.contains("icici") -> {
                bankCode = "ICICI"
                bankName = "ICICI Bank"
            }
            lowerSender.contains("axis") || lowerBody.contains("axis") || lowerSender.contains("utibr") -> {
                bankCode = "AXIS"
                bankName = "Axis Bank"
            }
            lowerSender.contains("kotak") || lowerBody.contains("kotak") -> {
                bankCode = "KOTAK"
                bankName = "Kotak Mahindra Bank"
            }
            lowerSender.contains("idfc") || lowerBody.contains("idfc") -> {
                bankCode = "IDFC"
                bankName = "IDFC FIRST Bank"
            }
            lowerSender.contains("pnb") || lowerBody.contains("punjab national") -> {
                bankCode = "OTHERS"
                bankName = "Punjab National Bank"
            }
            lowerSender.contains("bob") || lowerBody.contains("bank of baroda") -> {
                bankCode = "OTHERS"
                bankName = "Bank of Baroda"
            }
        }

        // 5. Extract Account Last 4
        var last4 = "0000"
        val acctMatcher = ACCOUNT_REGEX.matcher(body)
        if (acctMatcher.find()) {
            val candidate = acctMatcher.group(1)
            if (candidate != null && candidate.length in 3..4) {
                last4 = candidate.padStart(4, '0')
            }
        }

        // 6. Payment Method
        var method = PaymentMethod.OTHER
        when {
            lowerBody.contains("salary") -> method = PaymentMethod.SALARY
            lowerBody.contains("upi") || lowerBody.contains("vpa") -> method = PaymentMethod.UPI
            lowerBody.contains("neft") -> method = PaymentMethod.NEFT
            lowerBody.contains("rtgs") -> method = PaymentMethod.RTGS
            lowerBody.contains("imps") -> method = PaymentMethod.IMPS
            lowerBody.contains("atm") || lowerBody.contains("withdrawn") -> method = PaymentMethod.ATM
            lowerBody.contains("pos") -> method = PaymentMethod.POS
            lowerBody.contains("card") || lowerBody.contains("ending") -> method = PaymentMethod.CARD
            lowerBody.contains("refund") -> method = PaymentMethod.REFUND
            lowerBody.contains("cashback") -> method = PaymentMethod.CASHBACK
        }

        // 7. Reference Number & UTR
        var refNumber = ""
        val refMatcher = REF_REGEX.matcher(body)
        if (refMatcher.find()) {
            refNumber = refMatcher.group(1)?.trim() ?: ""
        }

        var utrNumber = ""
        val utrMatcher = UTR_REGEX.matcher(body)
        if (utrMatcher.find()) {
            utrNumber = utrMatcher.group(1)?.trim() ?: ""
        }

        // 8. Merchant / Beneficiary
        var merchant = ""
        if (type == TransactionType.CREDIT) {
            val fromMatcher = MERCHANT_FROM_REGEX.matcher(body)
            if (fromMatcher.find()) {
                merchant = cleanMerchant(fromMatcher.group(1) ?: "")
            }
        } else {
            val toMatcher = MERCHANT_TO_REGEX.matcher(body)
            if (toMatcher.find()) {
                merchant = cleanMerchant(toMatcher.group(1) ?: "")
            }
        }

        if (merchant.isBlank()) {
            merchant = when (method) {
                PaymentMethod.ATM -> "$bankCode ATM Withdrawal"
                PaymentMethod.SALARY -> "Salary Credit"
                PaymentMethod.UPI -> "UPI Payment"
                PaymentMethod.CARD -> "Card Transaction"
                else -> if (type == TransactionType.CREDIT) "Direct Credit" else "Bank Debit"
            }
        }

        // 9. Auto-assign Category
        val (categoryName, categoryColor) = categorize(lowerBody, merchant.lowercase(Locale.ROOT), method, type)

        // 10. Calculate Confidence Score
        var confidence = 0.0f
        if (foundAmount) confidence += 0.35f
        if (typeFound) confidence += 0.25f
        if (bankCode != "OTHERS") confidence += 0.20f
        if (last4 != "0000") confidence += 0.10f
        if (refNumber.isNotEmpty() || utrNumber.isNotEmpty() || merchant.isNotEmpty()) confidence += 0.10f

        val isFlagged = confidence < 0.70f || (bankCode == "OTHERS" && last4 == "0000")

        return ParsedSms(
            isValidTransaction = true,
            amount = parsedAmount,
            type = type,
            bankCode = bankCode,
            bankName = bankName,
            accountNumberLast4 = last4,
            paymentMethod = method,
            merchant = merchant,
            refNumber = refNumber,
            utrNumber = utrNumber,
            categoryName = categoryName,
            categoryColorHex = categoryColor,
            confidenceScore = confidence,
            isFlaggedForReview = isFlagged,
            rawBody = body,
            timestamp = smsTimestamp
        )
    }

    private fun cleanMerchant(raw: String): String {
        var clean = raw.trim()
        val stopWords = listOf("on", "at", "by", "ref", "avl", "bal", "limit", "call", "not", "use", "date")
        for (sw in stopWords) {
            val idx = clean.indexOf(" $sw ", ignoreCase = true)
            if (idx > 0) {
                clean = clean.substring(0, idx).trim()
            }
        }
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
                    lowerBody.contains("starbucks") || lowerBody.contains("cafe") ->
                "Food & Dining" to "#F59E0B"

            lowerBody.contains("amazon") || lowerBody.contains("flipkart") || lowerBody.contains("myntra") ||
                    lowerMerchant.contains("amazon") || lowerMerchant.contains("flipkart") ||
                    lowerMerchant.contains("croma") || lowerBody.contains("retail") ||
                    lowerBody.contains("supermarket") || lowerBody.contains("mall") ->
                "Shopping" to "#EC4899"

            lowerBody.contains("airtel") || lowerBody.contains("jio") || lowerBody.contains("bescom") ||
                    lowerBody.contains("electricity") || lowerBody.contains("water") ||
                    lowerBody.contains("gas bill") || lowerBody.contains("broadband") ||
                    lowerBody.contains("recharge") ->
                "Bills & Recharge" to "#3B82F6"

            lowerBody.contains("uber") || lowerBody.contains("ola") || lowerBody.contains("irctc") ||
                    lowerBody.contains("flight") || lowerBody.contains("makemytrip") ||
                    lowerBody.contains("indigo") || lowerBody.contains("metro") ||
                    lowerBody.contains("redbus") ->
                "Travel" to "#06B6D4"

            lowerBody.contains("petrol") || lowerBody.contains("fuel") || lowerBody.contains("hpcl") ||
                    lowerBody.contains("bpcl") || lowerBody.contains("ioc") ->
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

            lowerBody.contains("emi") || lowerBody.contains("loan repayment") ->
                "EMI" to "#E11D48"

            lowerBody.contains("insurance") || lowerBody.contains("lic") ->
                "Insurance" to "#0EA5E9"

            lowerBody.contains("cashback") ->
                "Cashback" to "#84CC16"

            lowerBody.contains("refund") || lowerBody.contains("reversal") ->
                "Refund" to "#10B981"

            method == PaymentMethod.UPI || method == PaymentMethod.NEFT || method == PaymentMethod.IMPS ->
                "Transfers" to "#64748B"

            type == TransactionType.CREDIT ->
                "Salary" to "#22C55E"

            else ->
                "Others" to "#94A3B8"
        }
    }
}
