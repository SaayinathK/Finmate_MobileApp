package com.example.financetracker.utils

import com.example.financetracker.models.Transaction
import org.apache.poi.ss.usermodel.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import kotlin.math.abs

/** Reads SWallet exports and the side-by-side Date / Subject / Amount account sheet. */
object AccountWorkbook {
    data class Result(val transactions: List<Transaction>, val skippedRows: Int, val recognisedSheets: Int)
    private val dateFormats = listOf("M/d/uuuu", "d/M/uuuu", "d-M-uuuu", "d MMM uuuu", "uuuu-MM-dd").map {
        DateTimeFormatter.ofPattern(it, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    }
    fun read(workbook: Workbook): Result {
        val output = mutableListOf<Transaction>()
        var skipped = 0
        var recognised = 0
        val formatter = DataFormatter(Locale.US)
        val evaluator = workbook.creationHelper.createFormulaEvaluator()
        fun value(cell: Cell?): String = if (cell == null) "" else formatter.formatCellValue(cell, evaluator).trim()
        fun numeric(cell: Cell?): Double? {
            if (cell == null) return null
            return try {
                val amount = when (cell.cellType) {
                    CellType.NUMERIC -> cell.numericCellValue
                    CellType.FORMULA -> evaluator.evaluate(cell)?.takeIf { it.cellType == CellType.NUMERIC }?.numberValue
                    else -> value(cell).replace(",", "").toDoubleOrNull()
                }
                amount?.takeIf { it.isFinite() && abs(it) > 0 && BigDecimal.valueOf(abs(it)).stripTrailingZeros().scale() <= 2 }
            } catch (_: Exception) { null }
        }
        fun date(cell: Cell?): LocalDate? {
            if (cell == null || value(cell).isBlank()) return null
            if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.dateCellValue.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            }
            for (format in dateFormats) try { return LocalDate.parse(value(cell), format) } catch (_: Exception) { }
            return PlannerRules.parseDate(value(cell))
        }
        for (sheet in workbook) {
            var header: Row? = null
            var labels: Map<String, Int> = emptyMap()
            var sideColumns: List<Int> = emptyList()
            for (index in 0..minOf(sheet.lastRowNum, 15)) {
                val row = sheet.getRow(index) ?: continue
                val cells = row.map { it.columnIndex to value(it).lowercase(Locale.ENGLISH) }
                val map = cells.associate { it.second to it.first }
                if (listOf("title", "amount", "category", "date", "type").all { it in map }) {
                    header = row; labels = map; break
                }
                val columns = cells.filter { (column, label) -> label == "date" && value(row.getCell(column + 1)).equals("Subject", true) && value(row.getCell(column + 2)).equals("Amount", true) }.map { it.first }
                if (columns.size >= 2) { header = row; sideColumns = columns.take(2); break }
            }
            val start = header?.rowNum ?: continue
            recognised++
            val carriedDates = mutableMapOf<Int, LocalDate>()
            for (rowIndex in (start + 1)..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                if (sideColumns.isEmpty()) {
                    if (row.all { value(it).isBlank() }) continue
                    val title = value(row.getCell(labels.getValue("title")))
                    val amount = numeric(row.getCell(labels.getValue("amount")))
                    val transactionDate = date(row.getCell(labels.getValue("date")))
                    val type = value(row.getCell(labels.getValue("type"))).lowercase(Locale.ENGLISH)
                    if (title.isBlank() || amount == null || transactionDate == null || type !in listOf("income", "expense")) { skipped++; continue }
                    val income = type == "income"
                    output.add(Transaction(title = title, amount = abs(amount) * if (income) 1 else -1, category = value(row.getCell(labels.getValue("category"))).ifBlank { "Other" }, date = PlannerRules.displayDate(transactionDate), type = if (income) "Income" else "Expense"))
                } else {
                    sideColumns.forEachIndexed { side, column ->
                        val rawDate = value(row.getCell(column))
                        if (rawDate.isNotBlank()) {
                            val parsed = date(row.getCell(column))
                            if (parsed == null) carriedDates.remove(column) else carriedDates[column] = parsed
                        }
                        val title = value(row.getCell(column + 1))
                        val rawAmount = value(row.getCell(column + 2))
                        if (title.isBlank() && rawAmount.isBlank()) return@forEachIndexed
                        val amount = numeric(row.getCell(column + 2))
                        val transactionDate = carriedDates[column]
                        if (title.isBlank() || amount == null || transactionDate == null) { skipped++; return@forEachIndexed }
                        output.add(Transaction(title = title, amount = abs(amount) * if (side == 0) 1 else -1, category = "Other", date = PlannerRules.displayDate(transactionDate), type = if (side == 0) "Income" else "Expense"))
                    }
                }
            }
        }
        return Result(output, skipped, recognised)
    }
    fun newEntries(existing: List<Transaction>, imported: List<Transaction>): List<Transaction> {
        fun key(tx: Transaction) = listOf(tx.title.trim(), BigDecimal.valueOf(abs(tx.amount)).setScale(2).toPlainString(), tx.category, tx.date, tx.type)
        val counts = existing.groupingBy(::key).eachCount().toMutableMap()
        return imported.filter { tx ->
            val k = key(tx); val count = counts[k] ?: 0
            if (count > 0) { counts[k] = count - 1; false } else true
        }
    }
}
