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
        super.onViewCreated(view, savedInstanceState)

        val loanTypes = arrayOf("RECEIVE", "PAY")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, loanTypes)
        binding.loanTypeSpinner.adapter = adapter

        binding.addLoanBtn.setOnClickListener {
            if (isSubmitting) return@setOnClickListener
            isSubmitting = true
            binding.addLoanBtn.isEnabled = false

            val personName = binding.personNameEditText.text.toString()
            val amount = binding.amountEditText.text.toString().toDoubleOrNull()
            val type = binding.loanTypeSpinner.selectedItem.toString()
            val note = binding.noteEditText.text.toString()

            if (personName.isNotEmpty() && amount != null) {
                val loan = Loan(
                    personName = personName,
                    amount = amount,
                    type = type,
                    note = note
                )
                Log.d("AddLoanDialog", "Inserting loan: $loan")
                lifecycleScope.launch {
                    try {
                        AppDatabase.getDatabase(requireContext()).loanDao().insertLoan(loan)
                        // Also insert corresponding transaction mapping (PAY -> Expense, RECEIVE -> Income)
                        val txType = if (loan.type == "PAY") "Expense" else "Income"
                        val txAmount = if (txType == "Income") loan.amount else -loan.amount
                        val tx = com.example.financetracker.models.Transaction(
                            title = loan.personName,
                            amount = txAmount,
                            category = "Loans",
                            date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date()),
                            type = txType
                        )
                        AppDatabase.getDatabase(requireContext()).transactionDao().insertTransaction(tx)
                        Log.d("AddLoanDialog", "Insert complete for: ${loan.personName}")
                    } catch (ex: Exception) {
                        Log.e("AddLoanDialog", "Insert failed", ex)
                    } finally {
                        // dismiss on main thread
                        dismiss()
                    }
                }
            } else {
                // re-enable to allow correction
                isSubmitting = false
                binding.addLoanBtn.isEnabled = true
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
