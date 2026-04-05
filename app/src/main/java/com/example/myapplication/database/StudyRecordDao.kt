package com.example.myapplication.database

import android.content.ContentValues
import android.content.Context
import com.example.myapplication.model.StudyRecord

class StudyRecordDao(context: Context) {
    private val dbHelper = DatabaseHelper(context)

    fun insertStudyRecord(wordId: Int, status: Int, nextReviewTime: String): Long {
        val db = dbHelper.writableDatabase
        // 先检查是否已存在
        val existing = getRecordByWordId(wordId)
        return if (existing != null) {
            updateStudyRecord(wordId, status, nextReviewTime).toLong()
        } else {
            val values = ContentValues().apply {
                put("word_id", wordId)
                put("status", status)
                put("review_count", 1)
                put("next_review_time", nextReviewTime)
            }
            db.insert("study_records", null, values)
        }
    }

    fun updateStudyRecord(wordId: Int, status: Int, nextReviewTime: String): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("status", status)
            put("next_review_time", nextReviewTime)
            put("last_review_time", System.currentTimeMillis().toString())
        }
        // 同时增加复习次数
        db.execSQL(
            "UPDATE study_records SET review_count = review_count + 1, status = ?, next_review_time = ?, last_review_time = datetime('now') WHERE word_id = ?",
            arrayOf(status.toString(), nextReviewTime, wordId.toString())
        )
        return 1
    }

    fun getTodayReviewWords(): List<Int> {
        val db = dbHelper.readableDatabase
        val wordIds = mutableListOf<Int>()
        try {
            val cursor = db.rawQuery(
                "SELECT word_id FROM study_records WHERE next_review_time <= datetime('now')",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    wordIds.add(it.getInt(0))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return wordIds
    }

    fun getRecordByWordId(wordId: Int): StudyRecord? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM study_records WHERE word_id = ?",
            arrayOf(wordId.toString())
        )
        return cursor.use {
            if (it.moveToFirst()) {
                StudyRecord(
                    id = it.getInt(it.getColumnIndexOrThrow("id")),
                    wordId = it.getInt(it.getColumnIndexOrThrow("word_id")),
                    status = it.getInt(it.getColumnIndexOrThrow("status")),
                    reviewCount = it.getInt(it.getColumnIndexOrThrow("review_count")),
                    lastReviewTime = it.getString(it.getColumnIndexOrThrow("last_review_time")) ?: "",
                    nextReviewTime = it.getString(it.getColumnIndexOrThrow("next_review_time")) ?: ""
                )
            } else null
        }
    }

    fun getTodayStudyStats(): Triple<Int, Int, Int> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """SELECT
                SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as correct,
                SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as partial,
                SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) as wrong
               FROM study_records
               WHERE date(last_review_time) = date('now')""",
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                Triple(
                    it.getInt(0),
                    it.getInt(1),
                    it.getInt(2)
                )
            } else Triple(0, 0, 0)
        }
    }

    fun getTotalStudiedCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM study_records", null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getTodayReviewCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM study_records WHERE date(last_review_time) = date('now')",
            null
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}
