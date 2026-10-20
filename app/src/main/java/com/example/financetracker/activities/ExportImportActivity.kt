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


        // Setup header (back button and title)
        val backArrow = findViewById<android.widget.ImageView>(com.example.financetracker.R.id.backArrow)
        val headerTitle = findViewById<android.widget.TextView>(com.example.financetracker.R.id.headerTitle)
        headerTitle?.text = "Export / Import Data"
        backArrow?.setOnClickListener {
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
        lifecycleScope.launch {
            binding.importButton.isEnabled = false
            try {
                val parsed = withContext(Dispatchers.IO) {
                    requireNotNull(contentResolver.openInputStream(uri)) { "Could not open file" }.use { input ->
                        WorkbookFactory.create(input).use { workbook -> com.example.financetracker.utils.AccountWorkbook.read(workbook) }
                    }
                }
                if (parsed.recognisedSheets == 0 || parsed.transactions.isEmpty()) {
                    AlertDialog.Builder(this@ExportImportActivity).setTitle("No valid transactions found")
                        .setMessage("Use a SWallet export with Title, Amount, Category, Date and Type columns, or your side-by-side Date, Subject and Amount income and expense sheet. Dates must include a year. No existing data was changed.")
                        .setPositiveButton("OK", null).show()
                } else {
                    val existing = db.transactionDao().getAllTransactionsRaw()
                    val additions = com.example.financetracker.utils.AccountWorkbook.newEntries(existing, parsed.transactions)
                    AlertDialog.Builder(this@ExportImportActivity).setTitle("Review Excel import")
                        .setMessage("${additions.size} new transactions to add.\n${parsed.transactions.size - additions.size} entries already present.\n${parsed.skippedRows} incomplete or invalid entries skipped.\n\nYour existing accounts will be kept. Side-by-side sheet entries use the Other category; you can categorise them later.")
                        .setPositiveButton("Import") { _, _ ->
                            lifecycleScope.launch {
                                try {
                                    // A batch insert is atomic and does not replace existing accounts.
                                    db.transactionDao().insertAll(additions)
                                    displayImportedFileNames(uri)
                                    Toast.makeText(this@ExportImportActivity, "Added ${additions.size} transactions", Toast.LENGTH_LONG).show()
                                } catch (_: Exception) { Toast.makeText(this@ExportImportActivity, "Could not import. Existing accounts were kept.", Toast.LENGTH_LONG).show() }
                            }
                        }.setNegativeButton("Cancel", null).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ExportImportActivity, "Import failed: ${e.message}. Existing accounts were kept.", Toast.LENGTH_LONG).show()
            } finally { binding.importButton.isEnabled = true }
        }
    }
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
