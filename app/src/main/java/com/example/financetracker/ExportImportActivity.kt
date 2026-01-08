package com.example.financetracker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financetracker.databinding.ActivityExportImportBinding
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class ExportImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportImportBinding
    private lateinit var importedFileAdapter: ImportedFileAdapter
    private val importedFileList = mutableListOf<ImportedFile>()

    private lateinit var credential: GoogleAccountCredential
    private val spreadsheetId = "1cBgR77YemGYl8Sq_zCNtKAqO_JNghHFdF-E0-vJO_yU"
    private val sheetName = "Sheet5"

    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>
    private lateinit var accountPickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActivityResultLaunchers()
        setupViews()
    }

    private fun setupActivityResultLaunchers() {
        exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let { uri ->
                exportToExcel(uri)
            }
        }

        importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let { uri ->
                importFromExcel(uri)
                displayImportedFileNames(uri)
            }
        }

        accountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringExtra("authAccount")?.let { accountName ->
                credential.selectedAccountName = accountName
                exportToGoogleSheet()
            }
        }
    }

    private fun setupViews() {
        binding.backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.exportTextButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Choose Export Option")
                .setItems(arrayOf("Export to Local XLSX", "Export to Google Sheet")) { _, which ->
                    when (which) {
                        0 -> exportToLocalXlsx()
                        1 -> pickGoogleAccount()
                    }
                }
                .show()
        }

        binding.importButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
            importLauncher.launch(intent)
        }

        binding.recyclerImportedFiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerImportedFileNames.layoutManager = LinearLayoutManager(this)
        importedFileAdapter = ImportedFileAdapter(importedFileList)
        binding.recyclerImportedFileNames.adapter = importedFileAdapter

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> { startActivity(Intent(this, HomeActivity::class.java)); true }
                R.id.history -> { startActivity(Intent(this, TransactionHistoryActivity::class.java)); true }
                R.id.analysis -> { startActivity(Intent(this, CategoryAnalysisActivity::class.java)); true }
                R.id.setting -> { startActivity(Intent(this, SettingsActivity::class.java)); true }
                else -> false
            }
        }
    }

    private fun exportToLocalXlsx() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_TITLE, "transactions.xlsx")
        }
        exportLauncher.launch(intent)
    }

    private fun pickGoogleAccount() {
        credential = GoogleAccountCredential.usingOAuth2(
            this, listOf("https://www.googleapis.com/auth/spreadsheets")
        )
        accountPickerLauncher.launch(credential.newChooseAccountIntent())
    }

    private fun exportToExcel(uri: Uri) {
        try {
            val transactions = StorageManager.getTransactions(this)

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

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                workbook.write(outputStream)
                workbook.close()
                showExportSuccessDialog()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun importFromExcel(uri: Uri) {
        try {
            val importedTransactions = mutableListOf<Transaction>()
            contentResolver.openInputStream(uri)?.use { input ->
                val workbook = WorkbookFactory.create(input)
                val sheet = workbook.getSheetAt(0)

                for (i in 1..sheet.lastRowNum) {
                    val row = sheet.getRow(i) ?: continue
                    val txn = Transaction(
                        title = row.getCell(0)?.stringCellValue ?: "",
                        amount = row.getCell(1)?.numericCellValue ?: 0.0,
                        category = row.getCell(2)?.stringCellValue ?: "",
                        date = row.getCell(3)?.stringCellValue ?: "",
                        type = row.getCell(4)?.stringCellValue ?: ""
                    )
                    importedTransactions.add(txn)
                }
                workbook.close()

                val existing = StorageManager.getTransactions(this).toMutableList()
                existing.addAll(importedTransactions)
                StorageManager.saveTransactions(this, existing)

                Toast.makeText(this, "Imported successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun exportToGoogleSheet() {
        lifecycleScope.launch {
            try {
                val transactions = StorageManager.getTransactions(this@ExportImportActivity)

                val data = transactions.map {
                    listOf(it.title, it.amount.toString(), it.category, it.date, it.type)
                }

                withContext(Dispatchers.IO) {
                    val transport = GoogleNetHttpTransport.newTrustedTransport()
                    val jsonFactory = GsonFactory.getDefaultInstance()

                    val service = Sheets.Builder(transport, jsonFactory, credential)
                        .setApplicationName("FinMate")
                        .build()

                    val body = ValueRange().setValues(data)

                    service.spreadsheets().values()
                        .append(spreadsheetId, "$sheetName!A2", body)
                        .setValueInputOption("USER_ENTERED")
                        .execute()
                }

                Toast.makeText(this@ExportImportActivity, "Exported to Google Sheet!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(this@ExportImportActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
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
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    private fun showExportSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Export Successful")
            .setMessage("Your transactions have been saved successfully.")
            .setPositiveButton("OK", null)
            .show()
    }
}
