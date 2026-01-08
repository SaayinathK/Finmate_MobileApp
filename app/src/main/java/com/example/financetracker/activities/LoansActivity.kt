package com.example.financetracker.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.financetracker.fragments.LoansListFragment
import com.google.android.material.tabs.TabLayoutMediator
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import com.example.financetracker.adapters.LoanAdapter
import com.example.financetracker.dao.LoanDao
import com.example.financetracker.database.AppDatabase
import com.example.financetracker.databinding.ActivityLoansBinding
import com.example.financetracker.dialogs.AddLoanDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LoansActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoansBinding
    private lateinit var loanDao: LoanDao
    private val monthList = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR).toString()
    private var selectedMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())

    // Cache latest loans from DB Flow so we can re-filter on month/year change
    private var allLoansCache: List<com.example.financetracker.models.Loan> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoansBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // Setup header (back button and title)
        val backArrow = findViewById<android.widget.ImageView>(com.example.financetracker.R.id.backArrow)
        val headerTitle = findViewById<android.widget.TextView>(com.example.financetracker.R.id.headerTitle)
        headerTitle?.text = "Loans / Debts"
        backArrow?.setOnClickListener { finish() }

        // Attach standard bottom navigation if present in layout
        com.example.financetracker.ui.BottomNavHelper.attach(this)

        val database = AppDatabase.getDatabase(this)
        loanDao = database.loanDao()

        // Set up pager with tabs: All |Received | Givven
        val pagerAdapter = LoansPagerAdapter(this)
        val viewPager = binding.viewPager
        viewPager.adapter = pagerAdapter
        // Keep all fragments created so each page gets data updates immediately
        viewPager.offscreenPageLimit = pagerAdapter.itemCount

        TabLayoutMediator(binding.filterTabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "All"
                1 -> "Received"
                else -> "Givven"
            }
        }.attach()

        // Setup date range picker similar to TransactionHistoryActivity
        binding.btnSelectDateRange.setOnClickListener {
            showMonthYearDialog { adapter ->
                // when month/year changed, reapply filter using cached loans
                applyFilterAndUpdatePages(adapter)
            }
        }

        updateSelectedDateDisplay()

        // Start a single collector that updates cache and pages whenever DB changes
        startLoanCollector(pagerAdapter)

        binding.addLoanBtn.setOnClickListener {
            showAddLoanDialog()
        }
    }

    private fun loadLoans(pagerAdapter: LoansPagerAdapter) {
        // no-op; collection happens in startLoanCollector
    }

    private fun startLoanCollector(pagerAdapter: LoansPagerAdapter) {
        lifecycleScope.launch {
            loanDao.getAllLoans().collect { loans ->
                allLoansCache = loans
                applyFilterAndUpdatePages(pagerAdapter)
            }
        }
    }

    private fun applyFilterAndUpdatePages(pagerAdapter: LoansPagerAdapter) {
        val monthFormatter = SimpleDateFormat("MMMM", Locale.getDefault())
        val yearFormatter = SimpleDateFormat("yyyy", Locale.getDefault())

        val filtered = allLoansCache.filter { loan ->
            try {
                val date = Date(loan.date)
                monthFormatter.format(date) == selectedMonth && yearFormatter.format(date) == selectedYear
            } catch (e: Exception) {
                false
            }
        }

        pagerAdapter.setLoans(filtered)
    }

    private fun updateSelectedDateDisplay() {
        binding.textSelectedMonthYear.text = "$selectedMonth $selectedYear"
    }

    private fun showMonthYearDialog(onApply: (LoansPagerAdapter) -> Unit) {
        val dialogLayout = LayoutInflater.from(this).inflate(com.example.financetracker.R.layout.dialog_month_year_filter, null)
        val monthSpinner = dialogLayout.findViewById<Spinner>(com.example.financetracker.R.id.spinnerMonth)
        val yearSpinner = dialogLayout.findViewById<Spinner>(com.example.financetracker.R.id.spinnerYear)

        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, monthList)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        monthSpinner.adapter = monthAdapter
        monthSpinner.setSelection(monthList.indexOf(selectedMonth))

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearList = (currentYear downTo 2015).map { it.toString() }
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yearList)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        yearSpinner.adapter = yearAdapter
        yearSpinner.setSelection(yearList.indexOf(selectedYear))

        AlertDialog.Builder(this)
            .setTitle("Filter by Month & Year")
            .setView(dialogLayout)
            .setPositiveButton("Apply") { _, _ ->
                selectedMonth = monthSpinner.selectedItem.toString()
                selectedYear = yearSpinner.selectedItem.toString()
                updateSelectedDateDisplay()
                val adapter = binding.viewPager.adapter
                if (adapter is LoansPagerAdapter) onApply(adapter)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddLoanDialog() {
        val dialog = AddLoanDialog()
        dialog.show(supportFragmentManager, "AddLoanDialog")
    }
}
