package com.example.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun testDebitTransactionParsing() {
        val body = "HDFC Bank: Rs 1,850.00 debited from a/c **9876 on 11-09-26 to SWIGGY UPI Ref 6291048291. Avl bal: Rs 27,050.00."
        val parsed = SmsParser.parse(body, "VK-HDFCBK", System.currentTimeMillis())

        assertEquals(ClassificationStatus.TRANSACTION, parsed.status)
        assertTrue(parsed.isValidTransaction)
        assertEquals(1850.0, parsed.amount, 0.001)
        assertEquals("HDFC", parsed.bankCode)
        assertEquals("9876", parsed.accountNumberLast4)
        assertEquals("SWIGGY", parsed.merchant)
        assertEquals("6291048291", parsed.refNumber)
        assertFalse(parsed.isFlaggedForReview)
    }

    @Test
    fun testSalaryCreditTransactionParsing() {
        val body = "Dear SBI User, A/C ...1234 credited by Rs 32,000.00 on 11Sep26 by transfer from TECH LABS SALARY. Ref No SAL83921. Avl Bal Rs 80,250.00."
        val parsed = SmsParser.parse(body, "BZ-SBIINB", System.currentTimeMillis())

        assertEquals(ClassificationStatus.TRANSACTION, parsed.status)
        assertTrue(parsed.isValidTransaction)
        assertEquals(32000.0, parsed.amount, 0.001)
        assertEquals("SBI", parsed.bankCode)
        assertEquals("1234", parsed.accountNumberLast4)
        assertEquals("SAL83921", parsed.refNumber)
    }

    @Test
    fun testStrictOtpExclusion() {
        val body = "Your OTP for transaction of INR 2,500.00 on HDFC Card 1234 is 482910. Do not share with anyone."
        val parsed = SmsParser.parse(body, "HDFCBK", System.currentTimeMillis())

        assertEquals(ClassificationStatus.IGNORED, parsed.status)
        assertFalse(parsed.isValidTransaction)
    }

    @Test
    fun testBalanceInquiryExclusion() {
        val body = "Dear Customer, Avl Bal for A/c *5678 is Rs 14,230.50 as on 10-09-26. Thank you for banking with ICICI."
        val parsed = SmsParser.parse(body, "ICICIB", System.currentTimeMillis())

        assertEquals(ClassificationStatus.IGNORED, parsed.status)
        assertFalse(parsed.isValidTransaction)
    }

    @Test
    fun testPromotionalExclusion() {
        val body = "Get personal loan up to Rs 5,00,000 at just 10.5% interest! Apply now at loan.bank.com"
        val parsed = SmsParser.parse(body, "AXISBK", System.currentTimeMillis())

        assertEquals(ClassificationStatus.IGNORED, parsed.status)
        assertFalse(parsed.isValidTransaction)
    }

    @Test
    fun testNoAccountDoesNotDefaultTo0000() {
        val body = "Rs 450.00 debited at STARBUCKS via UPI Ref 999888."
        val parsed = SmsParser.parse(body, "HDFCBK", System.currentTimeMillis())

        assertEquals(ClassificationStatus.TRANSACTION, parsed.status)
        assertTrue(parsed.isValidTransaction)
        assertEquals(450.0, parsed.amount, 0.001)
        assertEquals("", parsed.accountNumberLast4)
    }

    @Test
    fun testKotakReceivedCreditTransactionParsing() {
        val body = "Received Rs.2.00 in your Kotak Bank AC 3453 from ANKIT CHAUDHARY on 12-09-26.UPI Ref:625576937179"
        val testTimestamp = 1726130000000L
        val parsed = SmsParser.parse(body, "VM-KOTAKB", testTimestamp)

        assertEquals(ClassificationStatus.TRANSACTION, parsed.status)
        assertTrue(parsed.isValidTransaction)
        assertEquals(2.00, parsed.amount, 0.001)
        assertEquals(com.example.data.model.TransactionType.CREDIT, parsed.type)
        assertEquals("KOTAK", parsed.bankCode)
        assertEquals("Kotak Mahindra Bank", parsed.bankName)
        assertEquals("3453", parsed.accountNumberLast4)
        assertEquals("625576937179", parsed.refNumber)
        assertEquals("ANKIT CHAUDHARY", parsed.merchant)
        assertEquals(testTimestamp, parsed.timestamp)
        assertFalse(parsed.isFlaggedForReview)
    }

    @Test
    fun testKotakReceivedCreditParsingWithUnknownSender() {
        val body = "Received Rs.2.00 in your Kotak Bank AC 3453 from ANKIT CHAUDHARY on 12-09-26.UPI Ref:625576937179"
        val parsed = SmsParser.parse(body, "", System.currentTimeMillis())

        assertEquals(ClassificationStatus.TRANSACTION, parsed.status)
        assertTrue(parsed.isValidTransaction)
        assertEquals("KOTAK", parsed.bankCode)
        assertEquals("3453", parsed.accountNumberLast4)
        assertEquals("625576937179", parsed.refNumber)
        assertEquals("ANKIT CHAUDHARY", parsed.merchant)
    }

    @Test
    fun testAllMajorIndianBanksHeaderIdentification() {
        val testCases = listOf(
            Triple("AXISBK", "INR 500.00 debited from A/c no. XX1234 on 10-09-26 to ZOMATO. Avl bal INR 10,000", "AXIS"),
            Triple("ICICIB", "Your A/C 9876 is debited for INR 1,200.00 on 12-SEP-26. Info: UPI-SWIGGY. Bal: INR 45,000.00", "ICICI"),
            Triple("PNBSMS", "Rs 350.00 debited from A/C *4321 on 10/09/2026 via UPI txn to AMAZON", "PNB"),
            Triple("BOBSMS", "A/C *6543 Debited with INR 2,000.00 on 11-09-2026. Ref: UPI/629104", "BOB"),
            Triple("CANBNK", "Your Canara Bank A/c ending 7890 credited by Rs 5,000.00 on 12-09-2026 via IMPS", "CANARA"),
            Triple("UBISMS", "Union Bank: INR 750.00 debited from A/c 2345 on 10-09-26 at SHELL", "UNION"),
            Triple("INDBNK", "Indian Bank A/c ..5678 Credited by Rs 15,000.00 on 09-09-2026 ref 998877", "INDIAN"),
            Triple("CBISMS", "Central Bank of India: Rs 1,000.00 debited from Ac 3456 on 10-09-26", "CENTRAL"),
            Triple("IDFCPB", "IDFC FIRST Bank: Rs 4,500.00 spent on your Card ending 1122 at FLIPKART", "IDFC"),
            Triple("YESBNK", "YES Bank: INR 800.00 debited from A/C *8899 on 11-09-26 via UPI Ref 112233", "YES"),
            Triple("FEDBNK", "Federal Bank A/C ...9900 credited with Rs 2,500.00 on 12-09-26 via NEFT", "FEDERAL"),
            Triple("INDBK", "IndusInd Bank: INR 3,200.00 debited from A/c 5544 on 10-09-26 to UBER", "INDUSIND"),
            Triple("BNDHN", "Bandhan Bank: Rs 600.00 debited from A/C 6677 on 08-09-26", "BANDHAN"),
            Triple("PYTM", "Paytm Payments Bank: Rs.150.00 received in A/c 8811 from RAHUL", "PAYTM"),
            Triple("AIRTELPB", "Airtel Payments Bank: INR 200.00 debited from A/c 9922 on 10-09-26", "AIRTEL"),
            Triple("AUFINB", "AU Small Finance Bank: Rs 1,800.00 credited to A/c 3344 on 11-09-26", "AU")
        )

        for ((sender, body, expectedBankCode) in testCases) {
            val parsed = SmsParser.parse(body, sender, System.currentTimeMillis())
            assertEquals("Failed for sender $sender", ClassificationStatus.TRANSACTION, parsed.status)
            assertTrue("Failed validTx for $sender", parsed.isValidTransaction)
            assertEquals("Failed bankCode for $sender", expectedBankCode, parsed.bankCode)
        }
    }

    @Test
    fun testVariedTransactionKeywords() {
        // Test "transferred"
        val body1 = "Rs 1,500.00 transferred from your SBI A/c 1234 to Mr Sharma on 10-09-26. UPI Ref 554433."
        val parsed1 = SmsParser.parse(body1, "SBIPAY", System.currentTimeMillis())
        assertEquals(ClassificationStatus.TRANSACTION, parsed1.status)
        assertEquals(com.example.data.model.TransactionType.DEBIT, parsed1.type)
        assertEquals(1500.0, parsed1.amount, 0.001)

        // Test "deposited"
        val body2 = "INR 10,000.00 deposited to your HDFC A/C 9876 on 11-09-26 by cash. Avl bal INR 50,000."
        val parsed2 = SmsParser.parse(body2, "HDFCBK", System.currentTimeMillis())
        assertEquals(ClassificationStatus.TRANSACTION, parsed2.status)
        assertEquals(com.example.data.model.TransactionType.CREDIT, parsed2.type)
        assertEquals(10000.0, parsed2.amount, 0.001)

        // Test "dr:"
        val body3 = "ICICI Bank: Acct 5678 Dr: INR 450.00 on 12-09-26. UPI/625511/Swiggy. Bal: INR 12,000"
        val parsed3 = SmsParser.parse(body3, "ICICIB", System.currentTimeMillis())
        assertEquals(ClassificationStatus.TRANSACTION, parsed3.status)
        assertEquals(com.example.data.model.TransactionType.DEBIT, parsed3.type)
        assertEquals(450.0, parsed3.amount, 0.001)

        // Test "cr:"
        val body4 = "Axis Bank: Acct 4321 Cr: INR 2,200.00 on 12-09-26 via UPI Ref 998811"
        val parsed4 = SmsParser.parse(body4, "AXISBK", System.currentTimeMillis())
        assertEquals(ClassificationStatus.TRANSACTION, parsed4.status)
        assertEquals(com.example.data.model.TransactionType.CREDIT, parsed4.type)
        assertEquals(2200.0, parsed4.amount, 0.001)
    }
}

