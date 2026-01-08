package com.example.financetracker.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
import kotlinx.coroutines.flow.first


class AddTransactionActivity : AppCompatActivity() {
    private var currentTransaction: Transaction? = null

    private lateinit var binding: ActivityAddTransactionBinding
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private var editMode = false
    private var editTransactionId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
            // Set up back button in header
            val backArrow = findViewById<View>(R.id.backArrow)
            backArrow?.setOnClickListener { finish() }
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Set header title dynamically (after setContentView)
        val headerTitle = findViewById<TextView>(R.id.headerTitle)
        headerTitle?.text = if (intent.getBooleanExtra("editMode", false)) "Edit Transaction" else "Add Transaction"

        // Centralized bottom navigation
        com.example.financetracker.ui.BottomNavHelper.attach(this)
        setupCategoryDropdown()
        setupDatePicker()
        setupLoanOptions()
        handleEditMode()
        setupButtonListeners()
    }

    private fun setupLoanOptions() {
        val loanTypes = listOf("GIVVEN", "RECEIVED")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, loanTypes)
        binding.loanActionSpinner.setAdapter(adapter)

        binding.createLoanCheckbox.setOnCheckedChangeListener { _, checked ->
            binding.loanActionSpinner.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) {
                val titleText = binding.titleEditText.text.toString().trim()
                if (titleText.isNotEmpty()) {
                    binding.loanPersonEditText.visibility = View.GONE
                } else {
                    binding.loanPersonEditText.visibility = View.VISIBLE
                }
                // When checked, if person name is entered, auto-fill title
                binding.loanPersonEditText.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val name = binding.loanPersonEditText.text.toString().trim()
                        if (name.isNotEmpty()) {
                            binding.titleEditText.setText(name)
                            binding.loanPersonEditText.visibility = View.GONE
                        }
                    }
                }
            } else {
                binding.loanPersonEditText.visibility = View.GONE
            }
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

    // No-op: back button is handled via header include and findViewById

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
                    currentTransaction = it
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
        // Show delete button only in edit mode
        if (editMode) {
            binding.root.findViewById<View>(R.id.deleteButton)?.visibility = View.VISIBLE
            binding.root.findViewById<View>(R.id.deleteButton)?.setOnClickListener {
                // Step 1: Confirm deletion
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Delete Transaction")
                    .setMessage("Are you sure you want to delete this transaction? This may also affect related loans.")
                    .setPositiveButton("Delete") { _, _ ->
                        // Step 2: Confirm again
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Confirm Delete")
                            .setMessage("This action cannot be undone. Do you really want to delete?")
                            .setPositiveButton("Yes, Delete") { _, _ ->
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        currentTransaction?.let { tx ->
                                            val db = AppDatabase.getDatabase(this@AddTransactionActivity)
                                            db.transactionDao().deleteTransaction(tx)
                                            // Delete related loan if present
                                            val allLoansFlow = db.loanDao().getAllLoans()
                                            val allLoans: List<com.example.financetracker.models.Loan> = allLoansFlow.first()
                                            val relatedLoans = allLoans.filter { loan: com.example.financetracker.models.Loan ->
                                                loan.personName == tx.title && Math.abs(loan.amount) == Math.abs(tx.amount) && loan.note == tx.title
                                            }
                                            relatedLoans.forEach { loan: com.example.financetracker.models.Loan ->
                                                db.loanDao().deleteLoanById(loan.id)
                                            }
                                        }
                                    }
                                    Toast.makeText(this@AddTransactionActivity, "Transaction deleted", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this@AddTransactionActivity, TransactionHistoryActivity::class.java))
                                    finish()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            binding.root.findViewById<View>(R.id.deleteButton)?.visibility = View.GONE
        }
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

            // If user chose to also create a Loan and selected a loan type,
            // override the transaction polarity: "RECEIVED" -> Income, "GIVVEN" -> Expense
            var effectiveIsIncome = isIncome
            if (binding.createLoanCheckbox.isChecked) {
                val loanTypeSelected = binding.loanActionSpinner.adapter?.getItem(binding.loanActionSpinner.listSelection) as? String
                    ?: binding.loanActionSpinner.text.toString().trim()
                if (loanTypeSelected == "RECEIVED") effectiveIsIncome = true
                else if (loanTypeSelected == "GIVVEN") effectiveIsIncome = false
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
                    val db = AppDatabase.getDatabase(this@AddTransactionActivity)
                    val dao = db.transactionDao()
                    if (editMode) dao.updateTransaction(transaction)
                    else dao.insertTransaction(transaction)
                    // If user requested to also create a loan entry, insert or update it
                    if (binding.createLoanCheckbox.isChecked) {
                        val loanTypeSelected = binding.loanActionSpinner.adapter?.getItem(binding.loanActionSpinner.listSelection) as? String
                            ?: binding.loanActionSpinner.text.toString().trim()
                        val person = binding.titleEditText.text.toString().trim()
                        if (person.isNotEmpty() && (loanTypeSelected == "GIVVEN" || loanTypeSelected == "RECEIVED")) {
                            val loan = com.example.financetracker.models.Loan(personName = person, amount = amount.toDouble(), type = loanTypeSelected, note = person)
                            if (editMode) {
                                // Try to find and update related loan
                                val allLoansFlow = db.loanDao().getAllLoans()
                                val allLoans: List<com.example.financetracker.models.Loan> = allLoansFlow.first()
                                val relatedLoans = allLoans.filter { l: com.example.financetracker.models.Loan ->
                                    l.personName == person && l.note == person
                                }
                                if (relatedLoans.isNotEmpty()) {
                                    relatedLoans.forEach { oldLoan: com.example.financetracker.models.Loan ->
                                        val updatedLoan = loan.copy(id = oldLoan.id)
                                        db.loanDao().updateLoan(updatedLoan)
                                    }
                                } else {
                                    db.loanDao().insertLoan(loan)
                                }
                            } else {
                                db.loanDao().insertLoan(loan)
                            }
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
