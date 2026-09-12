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
}
