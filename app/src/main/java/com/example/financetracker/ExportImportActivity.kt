package com.example.financetracker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.financetracker.databinding.ActivityExportImportBinding

class ExportImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportImportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExportImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Back arrow click → HomeActivity
        binding.backArrow.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        // Export as JSON
        binding.exportJsonButton.setOnClickListener {
            // TODO: Implement export logic
            Toast.makeText(this, "Exported as JSON!", Toast.LENGTH_SHORT).show()
        }

        // Export as Text
        binding.exportTextButton.setOnClickListener {
            // TODO: Implement export logic
            Toast.makeText(this, "Exported as Text!", Toast.LENGTH_SHORT).show()
        }

        // Import Data
        binding.importButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(intent, 101)
        }

        // Back
        binding.backArrow.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> {
                    startActivity(Intent(this, HomeActivity::class.java))
                    true
                }
                R.id.history -> {
                    startActivity(Intent(this, TransactionHistoryActivity::class.java))
                    true
                }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                // TODO: Parse and import data from the selected file
                Toast.makeText(this, "Data imported from file.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
