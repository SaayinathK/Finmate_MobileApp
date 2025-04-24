package com.example.financetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.financetracker.databinding.ActivityTransactionHistoryBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionHistoryBinding
    private lateinit var adapter: TransactionAdapter
    private val transactions = mutableListOf<Transaction>()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backArrow.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        loadSavedTransactions()

        adapter = TransactionAdapter(
            items = transactions,
            onDelete = { index ->
                if (index in transactions.indices) {
                    transactions.removeAt(index)
                    saveTransactions()
                    adapter.notifyItemRemoved(index)
                    Toast.makeText(this, "Transaction deleted", Toast.LENGTH_SHORT).show()
                }
            },
            onEdit = { index ->
                if (index in transactions.indices) {
                    val txn = transactions[index]
                    val intent = Intent(this, AddTransactionActivity::class.java).apply {
                        putExtra("editMode", true)
                        putExtra("position", index)
                        putExtra("title", txn.title)
                        putExtra("amount", txn.amount)
                        putExtra("category", txn.category)
                        putExtra("date", txn.date)
                        putExtra("type", txn.type)
                    }
                    startActivity(intent)
                }
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        handleIncomingTransaction()

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    true
                }
                R.id.history -> true
                R.id.analysis -> {
                    startActivity(Intent(this, CategoryAnalysisActivity::class.java))
                    true
                }
                R.id.setting -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun handleIncomingTransaction() {
        val editMode = intent.getBooleanExtra("editMode", false)
        val position = intent.getIntExtra("position", -1)

        val title = intent.getStringExtra("title")
        val amount = intent.getDoubleExtra("amount", 0.0)
        val category = intent.getStringExtra("category")
        val date = intent.getStringExtra("date")
        val type = intent.getStringExtra("type")

        if (!title.isNullOrEmpty() && !category.isNullOrEmpty() && !date.isNullOrEmpty() && amount != 0.0) {
            val txn = Transaction(title, amount, category, date, type ?: "Other")

            if (editMode && position in transactions.indices) {
                transactions[position] = txn
                adapter.notifyItemChanged(position)
                Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show()
            } else {
                transactions.add(txn)
                adapter.notifyItemInserted(transactions.size - 1)
                Toast.makeText(this, "Transaction added", Toast.LENGTH_SHORT).show()
            }

            saveTransactions()
        }
    }

    private fun loadSavedTransactions() {
        val prefs = getSharedPreferences("FinMatePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("transactions", "[]")
        val type = object : TypeToken<MutableList<Transaction>>() {}.type
        transactions.clear()
        transactions.addAll(gson.fromJson(json, type))
    }

    private fun saveTransactions() {
        val prefs = getSharedPreferences("FinMatePrefs", Context.MODE_PRIVATE)
        val json = gson.toJson(transactions)
        prefs.edit().putString("transactions", json).apply()
    }
}
