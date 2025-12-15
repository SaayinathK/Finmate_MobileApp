package com.example.financetracker

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityAddTransactionBinding
import java.text.SimpleDateFormat
import java.util.*

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private lateinit var titleEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var categorySpinner: AutoCompleteTextView
    private lateinit var dateTextView: TextView
    private lateinit var incomeRadioButton: RadioButton
    private lateinit var expenseRadioButton: RadioButton
    private lateinit var saveButtonLayout: LinearLayout
    private lateinit var cancelButtonLayout: LinearLayout
    private lateinit var backArrow: ImageView

    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var editMode = false
    private var editPosition = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🧭 Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, HomeActivity::class.java)); true
                }
                R.id.history -> {
                    startActivity(Intent(this, TransactionHistoryActivity::class.java)); true
                }
                R.id.analysis -> {
                    startActivity(Intent(this, CategoryAnalysisActivity::class.java)); true
                }
                R.id.setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java)); true
                }
                else -> false
            }
        }

        setupViews()
        setupListeners()
        checkEditMode()
    }

    private fun setupViews() {
        titleEditText = findViewById(R.id.titleEditText)
        amountEditText = findViewById(R.id.amountEditText)
        categorySpinner = findViewById(R.id.categorySpinner)
        dateTextView = findViewById(R.id.dateTextView)
        saveButtonLayout = findViewById(R.id.saveButton)
        cancelButtonLayout = findViewById(R.id.cancelButton)
        incomeRadioButton = findViewById(R.id.incomeRadioButton)
        expenseRadioButton = findViewById(R.id.expenseRadioButton)
        backArrow = findViewById(R.id.backArrow)

        val categories = listOf("Food", "Transport", "Shopping", "Health", "Bills")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        categorySpinner.setAdapter(adapter)
    }

    private fun setupListeners() {
        categorySpinner.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) categorySpinner.showDropDown()
        }

        categorySpinner.setOnClickListener {
            categorySpinner.showDropDown()
        }

        backArrow.setOnClickListener {
            finish()
        }

        dateTextView.setOnClickListener {
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    dateTextView.text = dateFormat.format(calendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }

        saveButtonLayout.setOnClickListener {
            saveTransaction()
        }

        cancelButtonLayout.setOnClickListener {
            finish()
        }
    }


    private fun checkEditMode() {
        editMode = intent.getBooleanExtra("editMode", false)
        if (editMode) {
            editPosition = intent.getIntExtra("position", -1)
            val title = intent.getStringExtra("title")
            val amount = intent.getDoubleExtra("amount", 0.0)
            val category = intent.getStringExtra("category")
            val date = intent.getStringExtra("date")
            val type = intent.getStringExtra("type")

            titleEditText.setText(title)
            amountEditText.setText(kotlin.math.abs(amount).toString())
            dateTextView.text = date
            if (type == "Income") incomeRadioButton.isChecked = true else expenseRadioButton.isChecked = true
            category?.let {
                categorySpinner.setText(it, false)
            }
        }
    }

    private fun saveTransaction() {
        val title = titleEditText.text.toString().trim()
        val amountStr = amountEditText.text.toString().trim()
        val category = categorySpinner.text.toString().trim()
        val date = dateTextView.text.toString().trim()
        val isIncome = incomeRadioButton.isChecked

        if (title.isEmpty() || amountStr.isEmpty() || category.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val finalAmount = if (isIncome) amount else -amount
        val type = if (isIncome) "Income" else "Expense"

        val transaction = Transaction(
            title = title,
            amount = finalAmount,
            category = category,
            date = date,
            type = type
        )

        if (editMode) {
            StorageManager.updateTransaction(this, editPosition, transaction)
            Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show()
        } else {
            StorageManager.saveTransaction(this, transaction)
            Toast.makeText(this, "Transaction saved", Toast.LENGTH_SHORT).show()
        }

        finish() // ✅ Go back without startActivity
    }
}
