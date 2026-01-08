package com.example.financetracker

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface SheetApi {
    @PATCH("{spreadsheetId}/values/{range}")
    fun updateSheet(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String,
        @Query("key") apiKey: String,
        @Body body: UpdateSheetRequest
    ): Call<UpdateSheetResponse>
}
