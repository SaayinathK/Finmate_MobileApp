package com.example.financetracker.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financetracker.R
import com.example.financetracker.adapters.ImportedFileAdapter
import com.example.financetracker.adapters.MonthlyTransactionAdapter
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.ActivityExportImportBinding
import com.example.financetracker.models.ImportedFile
import com.example.financetracker.models.MonthlyTransactionItem
import com.example.financetracker.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.*


class ExportImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportImportBinding
    private val exportRequestCode = 102
    private val importRequestCode = 101
    private lateinit var transactionAdapter: MonthlyTransactionAdapter
    private lateinit var importedFileAdapter: ImportedFileAdapter
    private val importedFileList = mutableListOf<ImportedFile>()
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        binding.backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // ✅ Updated Export Button with Popup for Custom File Name
        binding.exportTextButton.setOnClickListener {
            showExportFileNameInputDialog()
        }



        binding.importButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
            startActivityForResult(intent, importRequestCode)
        }

        // Centralized bottom navigation
        com.example.financetracker.ui.BottomNavHelper.attach(this)

        binding.recyclerImportedFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerImportedFileNames.layoutManager = LinearLayoutManager(this)
        importedFileAdapter = ImportedFileAdapter(importedFileList)
        binding.recyclerImportedFileNames.adapter = importedFileAdapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return

        when (requestCode) {
            exportRequestCode -> if (resultCode == Activity.RESULT_OK) exportToExcel(uri)
            importRequestCode -> if (resultCode == Activity.RESULT_OK) {
                importFromExcel(uri)
                displayImportedFileNames(uri)
            }
        }
    }

    private fun exportToExcel(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val transactions = db.transactionDao().getAllTransactionsRaw()
                if (transactions.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExportImportActivity, "No transactions to export.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Transactions")
                val headerRow = sheet.createRow(0)
                listOf("Title", "Amount", "Category", "Date", "Type").forEachIndexed { index, title ->
                    headerRow.createCell(index).setCellValue(title)
                }

                transactions.forEachIndexed { index, txn ->
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(txn.title)
                    row.createCell(1).setCellValue(txn.amount)
                    row.createCell(2).setCellValue(txn.category)
                    row.createCell(3).setCellValue(txn.date)
                    row.createCell(4).setCellValue(txn.type)
                }

                // ✅ Use OutputStream and handle errors properly
                withContext(Dispatchers.Main) {
                    try {
                        val outputStream = contentResolver.openOutputStream(uri)
                        if (outputStream != null) {
                            workbook.write(outputStream)
                            outputStream.flush()
                            outputStream.close()
                            workbook.close()
                            showExportSuccessDialog()
                        } else {
                            Toast.makeText(this@ExportImportActivity, "Failed to save the file. Invalid URI.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: SecurityException) {
                        Toast.makeText(this@ExportImportActivity, "Permission denied: ${e.message}", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@ExportImportActivity, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExportImportActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    private fun importFromExcel(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ✅ Check if there is already an imported file
                if (importedFileList.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ExportImportActivity, "You have already imported a file.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val transactions = mutableListOf<Transaction>()
                contentResolver.openInputStream(uri)?.use { input ->
                    val workbook = WorkbookFactory.create(input)
                    for (sheetIndex in 0 until workbook.numberOfSheets) {
                        val sheet = workbook.getSheetAt(sheetIndex)
                        if (sheet.physicalNumberOfRows == 0) continue

                        val uniqueTransactions = mutableSetOf<String>()

                        for (i in 1..sheet.lastRowNum) {
                            val row = sheet.getRow(i) ?: continue
                            val title = row.getCell(0)?.stringCellValue ?: "Unknown"
                            val amount = row.getCell(1)?.numericCellValue ?: 0.0
                            val category = row.getCell(2)?.stringCellValue ?: "Unknown"
                            val date = row.getCell(3)?.stringCellValue ?: "Unknown"
                            val type = row.getCell(4)?.stringCellValue ?: "Unknown"

                            val uniqueKey = "$title-$amount-$category-$date-$type"
                            if (uniqueTransactions.contains(uniqueKey)) continue // Skip duplicates

                            transactions.add(
                                Transaction(
                                    title = title,
                                    amount = amount,
                                    category = category,
                                    date = date,
                                    type = type
                                )
                            )
                            uniqueTransactions.add(uniqueKey)
                        }
                    }
                    workbook.close()
                }

                // ✅ Replace existing transactions with the new imported ones
                db.transactionDao().deleteAll()
                if (transactions.isNotEmpty()) {
                    db.transactionDao().insertAll(transactions)
                }

                withContext(Dispatchers.Main) {
                    // ✅ Display the imported file name only once
                    if (importedFileList.isEmpty()) {
                        displayImportedFileNames(uri)
                    }
                    Toast.makeText(this@ExportImportActivity, "Imported successfully! Existing transactions replaced.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExportImportActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ✅ Display the imported file name only once
    private fun displayImportedFileNames(uri: Uri) {
        val fileName = getFileNameFromUri(uri) ?: "imported_file.xlsx"
        binding.importedFilesLabel.visibility = View.VISIBLE
        binding.recyclerImportedFileNames.visibility = View.VISIBLE
        importedFileList.add(ImportedFile(fileName))
        importedFileAdapter.notifyItemInserted(importedFileList.size - 1)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) name = cursor.getString(nameIndex)
        }
        return name
    }

    private fun showExportSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Export Successful")
            .setMessage("Your transactions have been saved as an Excel file.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun groupTransactionsByMonth(transactions: List<Transaction>): List<MonthlyTransactionItem> {
        val groupedList = mutableListOf<MonthlyTransactionItem>()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        val grouped = transactions.groupBy {
            try {
                val date = sdf.parse(it.date)
                monthFormat.format(date!!)
            } catch (e: Exception) {
                "Unknown"
            }
        }

        grouped.toSortedMap(compareByDescending { it }).forEach { (month, txns) ->
            groupedList.add(MonthlyTransactionItem.Header(month))
            txns.forEach {
                groupedList.add(MonthlyTransactionItem.TransactionItem(it))
            }
        }

        return groupedList
    }

    // ✅ Show Input Dialog for Export File Name
    private fun showExportFileNameInputDialog() {
        val input = EditText(this)
        input.hint = "Enter file name (without extension)"

        AlertDialog.Builder(this)
            .setTitle("Export File")
            .setMessage("Enter a name for the exported file:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val fileName = input.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_TITLE, "$fileName.xlsx")
                    }
                    startActivityForResult(intent, exportRequestCode)
                } else {
                    Toast.makeText(this, "File name cannot be empty.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
