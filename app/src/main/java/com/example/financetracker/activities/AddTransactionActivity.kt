package com.example.financetracker.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.models.Loan
import com.example.financetracker.databinding.ActivityAddTransactionBinding
import com.example.financetracker.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*


class AddTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTransactionBinding
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var editMode = false
    private var editTransactionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Centralized bottom navigation
        com.example.financetracker.ui.BottomNavHelper.attach(this)
        setupCategoryDropdown()
        setupDatePicker()
        setupLoanOptions()
        setupBackButton()
        handleEditMode()
        setupButtonListeners()
    }

    private fun setupLoanOptions() {
        val actions = listOf("To Receive", "To Pay")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, actions)
        binding.loanActionSpinner.setAdapter(adapter)

        binding.createLoanCheckbox.setOnCheckedChangeListener { _, checked ->
            binding.loanPersonEditText.visibility = if (checked) View.VISIBLE else View.GONE
            binding.loanActionSpinner.visibility = if (checked) View.VISIBLE else View.GONE
        }
    }

    // Bottom navigation handled centrally via BottomNavHelper

    private fun setupCategoryDropdown() {
        val categories = listOf("Food", "Transport", "Shopping", "Health", "Bills")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.categorySpinner.setAdapter(adapter)

        binding.categorySpinner.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.categorySpinner.showDropDown()
        }
        binding.categorySpinner.setOnClickListener {
            binding.categorySpinner.showDropDown()
        }
    }

    private fun setupDatePicker() {
        binding.dateTextView.setOnClickListener {
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    val formatted = dateFormat.format(calendar.time)
                    binding.dateTextView.text = formatted
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.show()
        }
    }

    private fun setupBackButton() {
        binding.backArrow.setOnClickListener {
            finish()
        }
    }

    private fun handleEditMode() {
        editMode = intent.getBooleanExtra("editMode", false)
        if (editMode) {
            editTransactionId = intent.getIntExtra("transactionId", -1)
            lifecycleScope.launch {
                val transaction: Transaction? = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@AddTransactionActivity)
                        .transactionDao()
                        .getTransactionById(editTransactionId)
                }

                transaction?.let {
                    binding.titleEditText.setText(it.title)
                    binding.amountEditText.setText(kotlin.math.abs(it.amount).toString())
                    binding.dateTextView.text = it.date

                    // Try to parse the date and set calendar (ensure future selection works correctly)
                    try {
                        calendar.time = dateFormat.parse(it.date)!!
                    } catch (_: Exception) {}

                    if (it.type == "Income") binding.incomeRadioButton.isChecked = true
                    else binding.expenseRadioButton.isChecked = true
                    binding.categorySpinner.setText(it.category, false)
                }
            }
        } else {
            val formatted = dateFormat.format(calendar.time)
            binding.dateTextView.text = formatted
        }
    }

    private fun setupButtonListeners() {
        binding.saveButton.setOnClickListener {
            val title = binding.titleEditText.text.toString().trim()
            val amountStr = binding.amountEditText.text.toString().trim()
            val category = binding.categorySpinner.text.toString().trim()
            val date = binding.dateTextView.text.toString().trim()
            val isIncome = binding.incomeRadioButton.isChecked

            if (title.isEmpty() || amountStr.isEmpty() || category.isEmpty() || date.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // If user chose to also create a Loan and selected a loan action,
            // override the transaction polarity: "To Pay" -> Income, "To Receive" -> Expense
            var effectiveIsIncome = isIncome
            if (binding.createLoanCheckbox.isChecked) {
                val loanAction = binding.loanActionSpinner.text.toString().trim()
                if (loanAction == "To Pay") effectiveIsIncome = true
                else if (loanAction == "To Receive") effectiveIsIncome = false
            }

            val finalAmount = if (effectiveIsIncome) amount else -amount
            val type = if (effectiveIsIncome) "Income" else "Expense"

            val formattedDate = try {
                dateFormat.parse(date)?.let { dateFormat.format(it) } ?: date
            } catch (e: Exception) {
                date // fallback
            }

            val transaction = Transaction(
                id = if (editMode) editTransactionId else 0,
                title = title,
                amount = finalAmount,
                category = category,
                date = formattedDate,
                type = type
            )

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getDatabase(this@AddTransactionActivity).transactionDao()
                    if (editMode) dao.updateTransaction(transaction)
                    else dao.insertTransaction(transaction)
                    // If user requested to also create a loan entry, insert it
                    if (binding.createLoanCheckbox.isChecked) {
                        val person = binding.loanPersonEditText.text.toString().trim()
                        val loanAction = binding.loanActionSpinner.text.toString().trim()
                        if (person.isNotEmpty() && (loanAction == "To Receive" || loanAction == "To Pay")) {
                            val loanType = if (loanAction == "To Receive") "RECEIVE" else "PAY"
                            val loan = Loan(personName = person, amount = amount.toDouble(), type = loanType, note = title)
                            AppDatabase.getDatabase(this@AddTransactionActivity).loanDao().insertLoan(loan)
                        }
                    }
                }
                Toast.makeText(this@AddTransactionActivity, "Transaction saved", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this@AddTransactionActivity, TransactionHistoryActivity::class.java))
                finish()
            }
        }

        binding.cancelButton.setOnClickListener {
            finish()
        }
    }
}
