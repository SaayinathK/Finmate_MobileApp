package com.example.financetracker.fragments

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
        } else {
            loanAdapter = LoanAdapter(emptyList(), onAction = { loan ->
                showSettleAmountDialog(loan)
            }, onDelete = { loan ->
                // confirm and delete
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
            })
            binding.recycler.adapter = loanAdapter
        }
    }

    fun setLoans(loans: List<Loan>) {
        if (!isAdded) return
        when (mode) {
            "RECEIVE" -> {
                val list = loans.filter { it.type == "RECEIVE" }
                loanAdapter = LoanAdapter(list, onAction = { loan ->
                    showSettleAmountDialog(loan)
                }, onDelete = { loan ->
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
                })
                binding.recycler.adapter = loanAdapter
            }
            "PAY" -> {
                val list = loans.filter { it.type == "PAY" }
                loanAdapter = LoanAdapter(list, onAction = { loan ->
                    showSettleAmountDialog(loan)
                }, onDelete = { loan ->
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
                })
                binding.recycler.adapter = loanAdapter
            }
            else -> {
                // Aggregate per person
                val grouped = loans.groupBy { it.personName }
                val summaries = grouped.map { (person, items) ->
                    val receive = items.filter { it.type == "RECEIVE" }.sumOf { it.amount }
                    val pay = items.filter { it.type == "PAY" }.sumOf { it.amount }
                    com.example.financetracker.models.AccountSummary(person, receive, pay)
                }
                accountAdapter = AccountSummaryAdapter(summaries,
                    onReceive = { summary -> showPersonActionDialog(summary, false) },
                    onPay = { summary -> showPersonActionDialog(summary, true) }
                )
                binding.recycler.adapter = accountAdapter
            }
        }
    }

    private fun showPersonActionDialog(summary: com.example.financetracker.models.AccountSummary, isPay: Boolean) {
        val edit = EditText(requireContext())
        edit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val defaultVal = if (isPay) summary.totalPay else summary.totalReceive
        edit.setText(String.format(Locale.getDefault(), "%.2f", defaultVal))

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (isPay) "Pay ${summary.personName}" else "Receive ${summary.personName}")
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

                    val finalAmount = if (isPay) -amt else amt
                    val type = if (isPay) "Expense" else "Income"

                    val tx = Transaction(
                        id = 0,
                        title = if (isPay) "Pay - ${summary.personName}" else "Receive - ${summary.personName}",
                        amount = finalAmount,
                        category = "Loan",
                        date = dateStr,
                        type = type
                    )
                    db.transactionDao().insertTransaction(tx)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettleAmountDialog(loan: Loan) {
        val edit = EditText(requireContext())
        edit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        edit.setText(loan.amount.toString())

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Settle ${if (loan.type == "PAY") "Paid" else "Received"}")
            .setView(edit)
            .setPositiveButton("Apply") { _, _ ->
                val entered = edit.text.toString().trim()
                val paid = entered.toDoubleOrNull()
                if (paid == null || paid <= 0.0) {
                    Toast.makeText(requireContext(), "Enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(requireContext())
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val dateStr = sdf.format(Date())

                    val (finalAmount, txType) = if (loan.type == "PAY") {
                        // To Pay settlement -> Expense (negative amount)
                        Pair(-paid, "Expense")
                    } else {
                        // To Receive settlement -> Income (positive amount)
                        Pair(paid, "Income")
                    }

                    val tx = Transaction(
                        id = 0,
                        title = "Loan settlement - ${loan.personName}",
                        amount = finalAmount,
                        category = "Loan",
                        date = dateStr,
                        type = txType
                    )
                    db.transactionDao().insertTransaction(tx)

                    // If paid covers full loan amount (or more), delete; otherwise reduce loan amount
                    if (paid >= loan.amount - 0.0001) {
                        db.loanDao().deleteLoanById(loan.id)
                    } else {
                        val updated = loan.copy(amount = loan.amount - paid)
                        db.loanDao().updateLoan(updated)
                    }
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
