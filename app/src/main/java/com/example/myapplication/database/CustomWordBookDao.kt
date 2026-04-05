package com.example.myapplication.database

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.example.myapplication.model.CustomWordBook

class CustomWordBookDao(context: Context) {
    companion object {
        private const val TAG = "CustomWordBookDao"
    }

    private val dbHelper = DatabaseHelper(context)

    fun createCustomWordBook(bookName: String): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("book_name", bookName)
            put("word_count", 0)
            put("is_delete", 0)
        }
        val bookId = db.insert("custom_word_book", null, values)
        Log.d(TAG, "✅ 创建自定义词库: $bookName (ID: $bookId)")
        // 同步到 word_category 表
        val catValues = ContentValues().apply {
            put("category_name", bookName)
            put("word_count", 0)
            put("is_custom", 1)
        }
        db.insert("word_category", null, catValues)
        return bookId
    }

    fun deleteCustomWordBook(bookId: Int): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put("is_delete", 1) }
        return db.update("custom_word_book", values, "id = ?", arrayOf(bookId.toString()))
    }

    fun updateCustomWordBookName(bookId: Int, newName: String): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply { put("book_name", newName) }
        return db.update("custom_word_book", values, "id = ?", arrayOf(bookId.toString()))
    }

    fun getAllCustomWordBooks(): List<CustomWordBook> {
        val db = dbHelper.readableDatabase
        val books = mutableListOf<CustomWordBook>()
        try {
            val cursor = db.rawQuery(
                "SELECT * FROM custom_word_book WHERE is_delete = 0 ORDER BY create_time DESC",
                null
            )
            Log.d(TAG, "📊 查询自定义词库, 数据库返回 ${cursor.count} 条记录")
            cursor.use {
                while (it.moveToNext()) {
                    val book = CustomWordBook(
                        id = it.getInt(it.getColumnIndexOrThrow("id")),
                        bookName = it.getString(it.getColumnIndexOrThrow("book_name")),
                        createTime = it.getString(it.getColumnIndexOrThrow("create_time")) ?: "",
                        wordCount = it.getInt(it.getColumnIndexOrThrow("word_count")),
                        isDelete = it.getInt(it.getColumnIndexOrThrow("is_delete")) == 1
                    )
                    Log.d(TAG, "✅ 读取词库: ${book.bookName} (ID: ${book.id}, 单词数: ${book.wordCount})")
                    books.add(book)
                }
            }
            Log.d(TAG, "✅ 共加载 ${books.size} 个词库")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 获取自定义词库失败: ${e.message}", e)
            e.printStackTrace()
        }
        // 始终返回列表（可能为空，但永不为 null）
        return books
    }

    fun getCustomWordBookById(bookId: Int): CustomWordBook? {
        val db = dbHelper.readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT * FROM custom_word_book WHERE id = ? AND is_delete = 0",
                arrayOf(bookId.toString())
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return CustomWordBook(
                        id = it.getInt(it.getColumnIndexOrThrow("id")),
                        bookName = it.getString(it.getColumnIndexOrThrow("book_name")),
                        createTime = it.getString(it.getColumnIndexOrThrow("create_time")) ?: "",
                        wordCount = it.getInt(it.getColumnIndexOrThrow("word_count")),
                        isDelete = it.getInt(it.getColumnIndexOrThrow("is_delete")) == 1
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 查询词库 ID: $bookId 失败: ${e.message}", e)
        }
        return null
    }

    fun incrementWordCount(bookId: Int): Int {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE custom_word_book SET word_count = word_count + 1 WHERE id = ?",
            arrayOf(bookId.toString())
        )
        return 1
    }

    fun incrementWordCountByDelta(bookId: Int, delta: Int): Int {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE custom_word_book SET word_count = word_count + ? WHERE id = ?",
            arrayOf(delta.toString(), bookId.toString())
        )
        Log.d(TAG, "✅ 词库 ID: $bookId 单词数增加 $delta")
        return 1
    }

    fun decrementWordCount(bookId: Int): Int {
        val db = dbHelper.writableDatabase
        db.execSQL(
            "UPDATE custom_word_book SET word_count = MAX(0, word_count - 1) WHERE id = ?",
            arrayOf(bookId.toString())
        )
        return 1
    }

    fun getCustomWordBookByName(bookName: String): CustomWordBook? {
        val db = dbHelper.readableDatabase
        try {
            val cursor = db.rawQuery(
                "SELECT * FROM custom_word_book WHERE book_name = ? AND is_delete = 0",
                arrayOf(bookName)
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return CustomWordBook(
                        id = it.getInt(it.getColumnIndexOrThrow("id")),
                        bookName = it.getString(it.getColumnIndexOrThrow("book_name")),
                        createTime = it.getString(it.getColumnIndexOrThrow("create_time")) ?: "",
                        wordCount = it.getInt(it.getColumnIndexOrThrow("word_count")),
                        isDelete = it.getInt(it.getColumnIndexOrThrow("is_delete")) == 1
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 查询词库 '$bookName' 失败: ${e.message}", e)
        }
        return null
    }

    fun getTotalCustomWordCount(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM words WHERE is_custom = 1",
            null
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}
