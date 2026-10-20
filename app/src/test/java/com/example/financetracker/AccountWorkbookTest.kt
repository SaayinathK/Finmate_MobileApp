package com.example.financetracker

import com.example.financetracker.models.Transaction
import com.example.financetracker.utils.AccountWorkbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.*
import org.junit.Test

class AccountWorkbookTest {
    @Test fun importsScreenshotLayoutWithCarriedDatesAndIndependentSides() {
        XSSFWorkbook().use { book ->
            val sheet = book.createSheet("October")
            sheet.createRow(2).apply { listOf("Date", "Subject", "Amount", "Date", "Subject", "Amount").forEachIndexed { i, s -> createCell(i+1).setCellValue(s) } }
            sheet.createRow(3).apply {
                createCell(1).setCellValue("10/1/2026"); createCell(2).setCellValue("Commercial Bank"); createCell(3).setCellValue(536.59)
                createCell(4).setCellValue("10/2/2026"); createCell(5).setCellValue("Petrol"); createCell(6).setCellValue(1000.0)
            }
            sheet.createRow(4).apply { createCell(2).setCellValue("Purse Balance"); createCell(3).setCellValue(229.0); createCell(5).setCellValue("Milk Box"); createCell(6).setCellValue(140.0) }
            val result = AccountWorkbook.read(book)
            assertEquals(4, result.transactions.size)
            assertEquals("01 Oct 2026", result.transactions[0].date)
            assertEquals("02 Oct 2026", result.transactions[1].date)
            assertEquals(-1000.0, result.transactions[1].amount,0.0)
            assertEquals("01 Oct 2026", result.transactions[2].date)
            assertEquals("02 Oct 2026", result.transactions[3].date)
            assertEquals(0, result.skippedRows)
        }
    }
    @Test fun readsExportFormatAndSkipsIncompleteRows() {
        XSSFWorkbook().use { book ->
            val sheet = book.createSheet("Transactions")
            sheet.createRow(0).apply { listOf("Title", "Amount", "Category", "Date", "Type").forEachIndexed { i,s -> createCell(i).setCellValue(s) } }
            sheet.createRow(1).apply { createCell(0).setCellValue("Lunch"); createCell(1).setCellValue(1250.0); createCell(2).setCellValue("Food"); createCell(3).setCellValue("06 Oct 2026"); createCell(4).setCellValue("Expense") }
            sheet.createRow(2).apply { createCell(0).setCellValue("Broken"); createCell(1).setCellValue("invalid") }
            val result = AccountWorkbook.read(book)
            assertEquals(1,result.transactions.size); assertEquals(-1250.0,result.transactions.single().amount,0.0); assertEquals(1,result.skippedRows)
        }
    }
    @Test fun repeatedImportKeepsRealDuplicatePurchasesWithoutImportingThemTwice() {
        val tx = Transaction(title = "Bus", amount = -100.0, category = "Transport", date = "19 Oct 2026", type = "Expense")
        assertEquals(2, AccountWorkbook.newEntries(emptyList(),listOf(tx,tx)).size)
        assertEquals(1, AccountWorkbook.newEntries(listOf(tx),listOf(tx,tx)).size)
        assertTrue(AccountWorkbook.newEntries(listOf(tx,tx),listOf(tx,tx)).isEmpty())
    }
    @Test fun unknownWorkbookDoesNotProduceInventedTransactions() {
        XSSFWorkbook().use { book -> book.createSheet("Notes").createRow(0).createCell(0).setCellValue("Notes"); assertTrue(AccountWorkbook.read(book).transactions.isEmpty()) }
    }
}
