package com.example.financetracker.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import android.util.Log
import com.example.financetracker.R
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.DialogAddLoanBinding
import com.example.financetracker.models.Loan
import kotlinx.coroutines.launch

class AddLoanDialog : DialogFragment() {
    companion object {
        private const val ARG_LOAN = "loan"
        fun newInstance(loan: Loan?): AddLoanDialog {
            val dialog = AddLoanDialog()
            if (loan != null) {
                val args = Bundle()
                args.putSerializable(ARG_LOAN, loan)
                dialog.arguments = args
            }
            return dialog
        }
    }

    private lateinit var binding: DialogAddLoanBinding
    private var isSubmitting = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogAddLoanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Set header title and hide back button if present
        view.findViewById<android.widget.TextView>(com.example.financetracker.R.id.headerTitle)?.text = "Add Loan"
        val backArrow = view.findViewById<android.widget.ImageView>(com.example.financetracker.R.id.backArrow)
        backArrow?.visibility = View.GONE

        // If editing, pre-fill fields
        val editLoan = arguments?.getSerializable(ARG_LOAN) as? Loan
        if (editLoan != null) {
            binding.personNameEditText.setText(editLoan.personName)
            binding.amountEditText.setText(editLoan.amount.toString())
            val typeIndex = if (editLoan.type == "RECEIVED") 0 else 1
            binding.loanTypeSpinner.setSelection(typeIndex)
            binding.noteEditText.setText(editLoan.note ?: "")
            view.findViewById<android.widget.TextView>(com.example.financetracker.R.id.headerTitle)?.text = "Edit Loan"
            binding.tvAddLoan.text = "Update Loan"
        }
        super.onViewCreated(view, savedInstanceState)

        val loanTypes = arrayOf("RECEIVED", "GIVVEN")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, loanTypes)
        binding.loanTypeSpinner.adapter = adapter

        binding.saveButton.setOnClickListener {
            if (isSubmitting) return@setOnClickListener
            isSubmitting = true
            binding.tvAddLoan.isEnabled = false

            val personName = binding.personNameEditText.text.toString()
            val amount = binding.amountEditText.text.toString().toDoubleOrNull()
            val type = binding.loanTypeSpinner.selectedItem.toString()
            val note = binding.noteEditText.text.toString()

            if (personName.isNotEmpty() && amount != null) {
                val loan = Loan(
                    id = editLoan?.id ?: 0,
                    personName = personName,
                    amount = amount,
                    type = type,
                    note = note
                )
                Log.d("AddLoanDialog", if (editLoan != null) "Updating loan: $loan" else "Inserting loan: $loan")
                lifecycleScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(requireContext())
                        if (editLoan != null) {
                            db.loanDao().updateLoan(loan)
                            // Amend the old transaction instead of inserting a new one
                            val oldTx = db.transactionDao().getAllTransactionsRaw().find {
                                it.title == editLoan.personName &&
                                Math.abs(it.amount) == Math.abs(editLoan.amount) &&
                                it.category == "Loans"
                            }
                            if (oldTx != null) {
                                val txType = if (loan.type == "RECEIVED") "Income" else "Expense"
                                val txAmount = if (txType == "Income") loan.amount else -loan.amount
                                val updatedTx = oldTx.copy(
                                    title = loan.personName,
                                    amount = txAmount,
                                    type = txType,
                                    date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                                )
                                db.transactionDao().updateTransaction(updatedTx)
                            } else {
                                // If not found, insert as fallback
                                val txType = if (loan.type == "RECEIVED") "Income" else "Expense"
                                val txAmount = if (txType == "Income") loan.amount else -loan.amount
                                val tx = com.example.financetracker.models.Transaction(
                                    title = loan.personName,
                                    amount = txAmount,
                                    category = "Loans",
                                    date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                                    type = txType
                                )
                                db.transactionDao().insertTransaction(tx)
                            }
                        } else {
                            db.loanDao().insertLoan(loan)
                            // Insert new transaction
                            val txType = if (loan.type == "RECEIVED") "Income" else "Expense"
                            val txAmount = if (txType == "Income") loan.amount else -loan.amount
                            val tx = com.example.financetracker.models.Transaction(
                                title = loan.personName,
                                amount = txAmount,
                                category = "Loans",
                                date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                                type = txType
                            )
                            db.transactionDao().insertTransaction(tx)
                        }
                        Log.d("AddLoanDialog", if (editLoan != null) "Update complete for: ${loan.personName}" else "Insert complete for: ${loan.personName}")
                    } catch (ex: Exception) {
                        Log.e("AddLoanDialog", if (editLoan != null) "Update failed" else "Insert failed", ex)
                    } finally {
                        // dismiss on main thread
                        dismiss()
                    }
                }
            } else {
                // re-enable to allow correction
                isSubmitting = false
                binding.tvAddLoan.isEnabled = true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // make dialog wider so it doesn't appear too narrow on large screens
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
