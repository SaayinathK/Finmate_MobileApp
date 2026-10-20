package com.example.financetracker

import com.example.financetracker.utils.LoanEditRules
import org.junit.Assert.*
import org.junit.Test

class LoanEditRulesTest {
    @Test fun increasingPrincipalKeepsExistingPayments() {
        assertEquals(40_000L, LoanEditRules.remainingAfterPrincipalEdit(50_000, 30_000, 60_000))
        assertEquals(0L, LoanEditRules.remainingAfterPrincipalEdit(50_000, 30_000, 20_000))
    }
    @Test fun reducingPrincipalBelowPaymentsIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { LoanEditRules.remainingAfterPrincipalEdit(50_000, 30_000, 19_999) }
        assertThrows(IllegalArgumentException::class.java) { LoanEditRules.remainingAfterPrincipalEdit(50_000, 50_001, 60_000) }
    }
    @Test fun settledLoanCanBeCorrectedWithoutLosingItsPayments() {
        assertEquals(10_000L, LoanEditRules.remainingAfterPrincipalEdit(50_000, 0, 60_000))
        assertThrows(IllegalArgumentException::class.java) { LoanEditRules.remainingAfterPrincipalEdit(50_000, 0, 49_999) }
    }
    @Test fun replacingPaymentChangesOnlyItsContribution() {
        assertEquals(25_000L, LoanEditRules.remainingAfterPaymentEdit(50_000, 30_000, 10_000, 15_000))
        assertEquals(35_000L, LoanEditRules.remainingAfterPaymentEdit(50_000, 30_000, 10_000, 5_000))
    }
    @Test fun paymentEditCanSettleOrReopenLoan() {
        assertEquals(0L, LoanEditRules.remainingAfterPaymentEdit(50_000, 30_000, 10_000, 40_000))
        assertEquals(5_000L, LoanEditRules.remainingAfterPaymentEdit(50_000, 0, 10_000, 5_000))
    }
    @Test fun overpaymentAndInconsistentHistoryAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { LoanEditRules.remainingAfterPaymentEdit(50_000, 30_000, 10_000, 40_001) }
        assertThrows(IllegalArgumentException::class.java) { LoanEditRules.remainingAfterPaymentEdit(50_000, 49_999, 10_000, 5_000) }
        assertThrows(IllegalArgumentException::class.java) { LoanEditRules.remainingAfterPaymentEdit(50_000, 30_000, 10_000, 0) }
    }
    @Test fun centsHandleFloatingPointNoiseAndRejectInvalidMoney() {
        assertEquals(30L, LoanEditRules.cents(0.1 + 0.2))
        assertEquals(10_001L, LoanEditRules.cents(100.01))
        assertEquals(0L, LoanEditRules.cents(0.0))
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { LoanEditRules.cents(value) }
        }
    }
    @Test fun allCentBoundariesPreservePrincipalEqualsPaidPlusOutstanding() {
        for (original in 1L..100L) for (paid in 0L..original) {
            val revised = maxOf(1, paid) + 37
            val remaining = LoanEditRules.remainingAfterPrincipalEdit(original, original - paid, revised)
            assertEquals(revised, paid + remaining)
        }
    }
}
