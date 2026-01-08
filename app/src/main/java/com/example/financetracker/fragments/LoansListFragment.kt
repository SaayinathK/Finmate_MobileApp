package com.example.financetracker.fragments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import com.example.financetracker.database.AppDatabase
import android.widget.EditText
import android.text.InputType
import android.widget.Toast
import kotlinx.coroutines.launch
import com.example.financetracker.adapters.AccountSummaryAdapter
import com.example.financetracker.adapters.LoanAdapter
import com.example.financetracker.models.Transaction
import java.text.SimpleDateFormat
import java.util.*
import com.example.financetracker.databinding.FragmentLoansListBinding
import com.example.financetracker.models.Loan

class LoansListFragment : Fragment() {

    private var _binding: FragmentLoansListBinding? = null
    private val binding get() = _binding!!
    private var mode: String = "ALL"

    private var loanAdapter: LoanAdapter? = null
    private var accountAdapter: AccountSummaryAdapter? = null

    companion object {
        private const val ARG_MODE = "mode"
        fun newInstance(mode: String): LoansListFragment {
            val f = LoansListFragment()
            val b = Bundle()
            b.putString(ARG_MODE, mode)
            f.arguments = b
            return f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = arguments?.getString(ARG_MODE) ?: "ALL"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentLoansListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        if (mode == "ALL") {
            accountAdapter = AccountSummaryAdapter(emptyList(),
                onReceive = { summary -> showPersonActionDialog(summary, false) },
                onPay = { summary -> showPersonActionDialog(summary, true) }
            )
            binding.recycler.adapter = accountAdapter
        } else if (mode == "RECEIVED") {
            loanAdapter = LoanAdapter(
                emptyList(),
                section = "RECEIVED",
                onPay = { loan -> showSettleAmountDialog(loan) },
                onEdit = { loan -> showEditLoanDialog(loan) },
                onDelete = { loan ->
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete loan")
                        .setMessage("Are you sure you want to delete this loan entry?")
                        .setPositiveButton("Delete") { _, _ ->
                            lifecycleScope.launch {
                                AppDatabase.getDatabase(requireContext()).loanDao().deleteLoanById(loan.id)
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            )
            binding.recycler.adapter = loanAdapter
        } else if (mode == "GIVVEN") {
            loanAdapter = LoanAdapter(
                emptyList(),
                section = "GIVVEN",
                onReceive = { loan -> showSettleAmountDialog(loan) },
                onEdit = { loan -> showEditLoanDialog(loan) },
                onDelete = { loan ->
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete loan")
                        .setMessage("Are you sure you want to delete this loan entry?")
                        .setPositiveButton("Delete") { _, _ ->
                            lifecycleScope.launch {
                                AppDatabase.getDatabase(requireContext()).loanDao().deleteLoanById(loan.id)
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            )
            binding.recycler.adapter = loanAdapter
        }
    }

    fun setLoans(loans: List<Loan>) {
        if (!isAdded) return
        when (mode) {
            "ALL" -> {
                // Aggregate per person
                val grouped = loans.groupBy { it.personName }
                val summaries = grouped.map { (person, items) ->
                    val receive = items.filter { it.type == "RECEIVED" }.sumOf { it.amount }
                    val pay = items.filter { it.type == "GIVVEN" }.sumOf { it.amount }
                    val lastModified = items.maxOfOrNull { it.date } ?: 0L
                    Pair(lastModified, com.example.financetracker.models.AccountSummary(person, receive, pay))
                }
                val sortedSummaries = summaries.sortedByDescending { it.first }.map { it.second }
                accountAdapter = AccountSummaryAdapter(sortedSummaries,
                    onReceive = { summary -> showPersonActionDialog(summary, false) },
                    onPay = { summary -> showPersonActionDialog(summary, true) }
                )
                binding.recycler.adapter = accountAdapter
            }
            "RECEIVED" -> {
                val list = loans.filter { it.type == "RECEIVED" }
                    .sortedByDescending { it.date }
                loanAdapter = LoanAdapter(
                    list,
                    section = "RECEIVED",
                    onPay = { loan -> showSettleAmountDialog(loan) },
                    onEdit = { loan -> showEditLoanDialog(loan) },
                    onDelete = { loan ->
                        // Step 1: Confirm deletion
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Delete loan")
                            .setMessage("Are you sure you want to delete this loan entry? This will also delete the related transaction.")
                            .setPositiveButton("Delete") { _, _ ->
                                // Step 2: Confirm again
                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle("Confirm Delete")
                                    .setMessage("This action cannot be undone. Do you really want to delete?")
                                    .setPositiveButton("Yes, Delete") { _, _ ->
                                        lifecycleScope.launch {
                                            val db = AppDatabase.getDatabase(requireContext())
                                            db.loanDao().deleteLoanById(loan.id)
                                            // Also delete related transaction
                                            val txList = db.transactionDao().getAllTransactionsRaw().filter {
                                                it.title == loan.personName &&
                                                Math.abs(it.amount) == Math.abs(loan.amount) &&
                                                it.category == "Loans"
                                            }
                                            for (tx in txList) {
                                                db.transactionDao().deleteTransaction(tx)
                                            }
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                )
                binding.recycler.adapter = loanAdapter
            }
            "GIVVEN" -> {
                val list = loans.filter { it.type == "GIVVEN" }
                    .sortedByDescending { it.date }
                loanAdapter = LoanAdapter(
                    list,
                    section = "GIVVEN",
                    onReceive = { loan -> showSettleAmountDialog(loan) },
                    onEdit = { loan -> showEditLoanDialog(loan) },
                    onDelete = { loan ->
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Delete loan")
                            .setMessage("Are you sure you want to delete this loan entry?")
                            .setPositiveButton("Delete") { _, _ ->
                                lifecycleScope.launch {
                                    AppDatabase.getDatabase(requireContext()).loanDao().deleteLoanById(loan.id)
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                )
                binding.recycler.adapter = loanAdapter
            }
        }
    }

    // Dummy edit dialog for now
    private fun showEditLoanDialog(loan: Loan) {
        val dialog = com.example.financetracker.dialogs.AddLoanDialog.newInstance(loan)
        dialog.show(parentFragmentManager, "EditLoanDialog")
    }

    private fun showPersonActionDialog(summary: com.example.financetracker.models.AccountSummary, isPay: Boolean) {
        val edit = EditText(requireContext())
        edit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val defaultVal = if (isPay) summary.totalPay else summary.totalReceive
        edit.setText(String.format(Locale.getDefault(), "%.2f", defaultVal))

        val dialogTitle = if (isPay) "Pay (Loan Paid)" else "Receive (Loan Cut)"

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(edit)
            .setPositiveButton("Apply") { _, _ ->
                val entered = edit.text.toString().trim()
                val amt = entered.toDoubleOrNull()
                if (amt == null || amt <= 0.0) {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(requireContext())
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val dateStr = sdf.format(Date())

                    val (finalAmount, txType, txTitle) = if (isPay) {
                        // Pay: Expense (reduces net)
                        Triple(-amt, "Expense", "${summary.personName}(Loan settlement)")
                    } else {
                        // Receive: Income (increases net)
                        Triple(amt, "Income", "${summary.personName}(Loan cut)")
                    }

                    val tx = Transaction(
                        id = 0,
                        title = txTitle,
                        amount = finalAmount,
                        category = "Loan",
                        date = dateStr,
                        type = txType
                    )
                    db.transactionDao().insertTransaction(tx)

                    // Update the loan record for this person
                    val allLoans = db.loanDao().getAllLoans().first()
                    val loansForPerson = allLoans.filter { it.personName == summary.personName }
                    val totalLoanAmount = loansForPerson.sumOf { loan -> loan.amount }
                    val newAmount = if (isPay) totalLoanAmount - amt else totalLoanAmount + amt
                    // Remove all old loans for this person
                    for (loan in loansForPerson) {
                        db.loanDao().deleteLoanById(loan.id)
                    }
                    // Add a new loan record with updated amount if not zero
                    if (kotlin.math.abs(newAmount) > 0.0001) {
                        val newLoan = Loan(
                            id = 0,
                            personName = summary.personName,
                            amount = newAmount,
                            type = if (newAmount >= 0) "RECEIVED" else "GIVVEN",
                            date = System.currentTimeMillis()
                        )
                        db.loanDao().insertLoan(newLoan)
                    }
                    // Parent activity's collector will update the UI
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettleAmountDialog(loan: Loan) {
        val edit = EditText(requireContext())
        edit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        edit.setText(loan.amount.toString())

        val isPay = when (loan.type) {
            "RECEIVED" -> true   // Paying back a received loan (reduces net)
            "GIVVEN" -> false    // Receiving back a given loan (increases net)
            else -> false
        }
        val dialogTitle = when (loan.type) {
            "RECEIVED" -> "Pay (Loan Paid)"
            "GIVVEN" -> "Receive (Loan Cut)"
            else -> "Settle Loan"
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setView(edit)
            .setPositiveButton("Apply") { _, _ ->
                val entered = edit.text.toString().trim()
                val amt = entered.toDoubleOrNull()
                if (amt == null || amt <= 0.0) {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(requireContext())
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val dateStr = sdf.format(Date())

                    val (finalAmount, txType, txTitle) = if (isPay) {
                        // Paying a received loan: Expense (reduces net)
                        Triple(-amt, "Expense", "Loan paid - ${loan.personName}")
                    } else {
                        // Receiving a given loan: Income (increases net)
                        Triple(amt, "Income", "Loan received - ${loan.personName}")
                    }

                    val tx = Transaction(
                        id = 0,
                        title = txTitle,
                        amount = finalAmount,
                        category = "Loan",
                        date = dateStr,
                        type = txType
                    )
                    db.transactionDao().insertTransaction(tx)

                    // If paid/received covers full loan amount (or more), delete; otherwise reduce loan amount
                    if (amt >= loan.amount - 0.0001) {
                        db.loanDao().deleteLoanById(loan.id)
                    } else {
                        val updated = loan.copy(amount = loan.amount - amt)
                        db.loanDao().updateLoan(updated)
                    }

                    // No manual refresh here; parent activity will update fragment via collector
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
