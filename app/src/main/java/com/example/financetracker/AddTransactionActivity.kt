package com.example.financetracker

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import com.example.financetracker.databinding.ActivityAddTransactionBinding

class AddTransactionActivity : AppCompatActivity() {

    private lateinit var titleEditText: EditText
    private lateinit var amountEditText: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var dateTextView: TextView
    private lateinit var incomeRadioButton: RadioButton
    private lateinit var expenseRadioButton: RadioButton
    private lateinit var saveButtonLayout: LinearLayout
    private lateinit var cancelButtonLayout: LinearLayout
    private lateinit var backArrow: ImageView
    private lateinit var binding: ActivityAddTransactionBinding


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

        // Bind views
        titleEditText = findViewById(R.id.titleEditText)
        amountEditText = findViewById(R.id.amountEditText)
        categorySpinner = findViewById(R.id.categorySpinner)
        dateTextView = findViewById(R.id.dateTextView)
        saveButtonLayout = findViewById(R.id.saveButton)
        cancelButtonLayout = findViewById(R.id.cancelButton)
        incomeRadioButton = findViewById(R.id.incomeRadioButton)
        expenseRadioButton = findViewById(R.id.expenseRadioButton)
        backArrow = findViewById(R.id.backArrow)

        // Setup category spinner
        val categories = listOf("Select Category", "Food", "Transport", "Salary", "Shopping", "Bills", "Other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = adapter

        // Back arrow
        backArrow.setOnClickListener {
            finish()
        }

        // Date picker
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

        // Check if this is edit mode
        editMode = intent.getBooleanExtra("editMode", false)
        if (editMode) {
            editPosition = intent.getIntExtra("position", -1)
            val title = intent.getStringExtra("title")
            val amount = intent.getDoubleExtra("amount", 0.0)
            val category = intent.getStringExtra("category")
            val date = intent.getStringExtra("date")
            val type = intent.getStringExtra("type")

            // Pre-fill data
            titleEditText.setText(title)
            amountEditText.setText(kotlin.math.abs(amount).toString())
            dateTextView.text = date
            if (type == "Income") incomeRadioButton.isChecked = true else expenseRadioButton.isChecked = true
            val categoryIndex = categories.indexOf(category)
            if (categoryIndex != -1) categorySpinner.setSelection(categoryIndex)
        }

        // Save button logic
        saveButtonLayout.setOnClickListener {
            val title = titleEditText.text.toString().trim()
            val amountStr = amountEditText.text.toString().trim()
            val category = categorySpinner.selectedItem.toString()
            val date = dateTextView.text.toString().trim()
            val isIncome = incomeRadioButton.isChecked

            if (title.isEmpty() || amountStr.isEmpty() || category == "Select Category" || date == "Select Date") {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalAmount = if (isIncome) amount else -amount
            val type = if (isIncome) "Income" else "Expense"

            val resultIntent = Intent(this, TransactionHistoryActivity::class.java).apply {
                putExtra("title", title)
                putExtra("amount", finalAmount)
                putExtra("category", category)
                putExtra("date", date)
                putExtra("type", type)

                if (editMode) {
                    putExtra("editMode", true)
                    putExtra("position", editPosition)
                }
            }
            startActivity(resultIntent)
            finish()
        }

        cancelButtonLayout.setOnClickListener {
            finish()
        }


    }

}
