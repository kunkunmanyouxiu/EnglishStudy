package com.example.myapplication.database

import android.content.ContentValues
import android.content.Context
import com.example.myapplication.model.ErrorWord

class ErrorWordDao(context: Context) {
    private val dbHelper = DatabaseHelper(context)

    fun insertErrorWord(wordId: Int, errorAnswer: String): Long {
        val db = dbHelper.writableDatabase
        val existing = getErrorWordByWordId(wordId)
        return if (existing != null) {
            db.execSQL(
                "UPDATE error_words SET error_count = error_count + 1, error_answer = ?, last_error_time = datetime('now') WHERE word_id = ?",
                arrayOf(errorAnswer, wordId.toString())
            )
            existing.id.toLong()
        } else {
            val values = ContentValues().apply {
                put("word_id", wordId)
                put("error_count", 1)
                put("error_answer", errorAnswer)
            }
            db.insert("error_words", null, values)
        }
    }

    fun getAllErrorWords(): List<ErrorWord> {
        val db = dbHelper.readableDatabase
        val errors = mutableListOf<ErrorWord>()
        try {
            val cursor = db.rawQuery(
                """SELECT e.*, w.word, w.definition, w.phonetic
                   FROM error_words e
                   JOIN words w ON e.word_id = w.id
                   ORDER BY e.last_error_time DESC""",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    errors.add(
                        ErrorWord(
                            id = it.getInt(it.getColumnIndexOrThrow("id")),
                            wordId = it.getInt(it.getColumnIndexOrThrow("word_id")),
                            errorCount = it.getInt(it.getColumnIndexOrThrow("error_count")),
                            lastErrorTime = it.getString(it.getColumnIndexOrThrow("last_error_time")) ?: "",
                            errorAnswer = it.getString(it.getColumnIndexOrThrow("error_answer")) ?: "",
                            word = it.getString(it.getColumnIndexOrThrow("word")),
                            definition = it.getString(it.getColumnIndexOrThrow("definition")),
                            phonetic = it.getColumnIndex("phonetic").let { idx ->
                                if (idx >= 0 && !it.isNull(idx)) it.getString(idx) else null
                            }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return errors
    }

    fun deleteErrorWord(wordId: Int): Int {
        val db = dbHelper.writableDatabase
        return db.delete("error_words", "word_id = ?", arrayOf(wordId.toString()))
    }

    fun getErrorWordByWordId(wordId: Int): ErrorWord? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM error_words WHERE word_id = ?",
            arrayOf(wordId.toString())
        )
        return cursor.use {
            if (it.moveToFirst()) {
                ErrorWord(
                    id = it.getInt(it.getColumnIndexOrThrow("id")),
                    wordId = it.getInt(it.getColumnIndexOrThrow("word_id")),
                    errorCount = it.getInt(it.getColumnIndexOrThrow("error_count")),
                    errorAnswer = it.getString(it.getColumnIndexOrThrow("error_answer")) ?: ""
                )
            } else null
        }
    }

    fun getTotalErrorCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM error_words", null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}
