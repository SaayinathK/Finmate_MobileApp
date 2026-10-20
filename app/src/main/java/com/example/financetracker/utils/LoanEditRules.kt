package com.example.financetracker.utils

import java.math.BigDecimal
import java.math.RoundingMode

/** All edit arithmetic uses integer cents, including paid-off and partially paid loans. */
object LoanEditRules {
    fun cents(value: Double): Long {
        require(value.isFinite() && value >= 0) { "Invalid stored amount" }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }
    fun remainingAfterPrincipalEdit(original: Long, outstanding: Long, revised: Long): Long {
        require(original >= outstanding && outstanding >= 0 && revised > 0) { "Invalid loan amount" }
        val paid = original - outstanding
        require(revised >= paid) { "Original amount cannot be less than payments already recorded" }
        return revised - paid
    }
    fun remainingAfterPaymentEdit(original: Long, outstanding: Long, oldPayment: Long, revised: Long): Long {
        require(original >= outstanding && outstanding >= 0 && oldPayment > 0 && revised > 0) { "Invalid payment amount" }
        val paid = original - outstanding
        require(oldPayment <= paid) { "Payment history does not match this loan" }
        val otherPayments = paid - oldPayment
        require(revised <= original - otherPayments) { "Payment exceeds the remaining loan amount" }
        return original - otherPayments - revised
    }
}
