package com.example.myapplication.database

import android.content.Context

class LearnedWordDao(context: Context) {
    private val dbHelper = DatabaseHelper(context)

    fun markWordsLearned(wordIds: List<Int>, learnedDate: String) {
        if (wordIds.isEmpty()) return
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                "INSERT OR IGNORE INTO learned_words (word_id, learned_date) VALUES (?, ?)"
            )
            wordIds.forEach { id ->
                stmt.clearBindings()
                stmt.bindLong(1, id.toLong())
                stmt.bindString(2, learnedDate)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getLearnedDates(): List<String> {
        val db = dbHelper.readableDatabase
        val dates = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT DISTINCT learned_date FROM learned_words ORDER BY learned_date DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                dates.add(it.getString(0))
            }
        }
        return dates
    }
}

