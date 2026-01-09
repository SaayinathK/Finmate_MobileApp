package com.example.financetracker

data class UpdateSheetResponse(
    val spreadsheetId: String,
    val updatedRange: String,
    val updatedRows: Int,
    val updatedColumns: Int,
    val updatedCells: Int
)
