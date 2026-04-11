package com.example.myapplication.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.myapplication.model.Word

class WordDao(private val context: Context) {
    private val dbHelper = DatabaseHelper(context)

    fun getAllWordsByCategory(category: String): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            // 先查询 word_category 表，判断是标准分类还是自定义词库
            val catCursor = db.rawQuery(
                "SELECT is_custom FROM word_category WHERE category_name = ?",
                arrayOf(category)
            )
            val isCustom = catCursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }

            if (isCustom == 1) {
                // ✅ 自定义词库 - 需要根据词库名称获取ID，然后用custom_book_id查询
                val customBook = CustomWordBookDao(context).getCustomWordBookByName(category)
                if (customBook != null) {
                    val cursor = db.rawQuery(
                        "SELECT * FROM words WHERE is_custom = 1 AND custom_book_id = ?",
                        arrayOf(customBook.id.toString())
                    )
                    cursor.use { it.toWordList(words) }
                }
            } else {
                // ✅ 标准分类 - 根据 word_type 和 is_custom=0 查询
                val cursor = db.rawQuery(
                    "SELECT * FROM words WHERE word_type = ? AND is_custom = 0",
                    arrayOf(category)
                )
                cursor.use { it.toWordList(words) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun getAllWords(): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val cursor = db.rawQuery("SELECT * FROM words", null)
            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun addWord(word: String, phonetic: String, definition: String, wordType: String, isCustom: Int = 0, customBookId: Int = 0): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("word", word)
            put("phonetic", phonetic)
            put("definition", definition)
            put("word_type", wordType)
            put("is_custom", isCustom)
            put("custom_book_id", customBookId)
            put("is_collect", 0)
        }
        return db.insert("words", null, values)
    }

    fun insertWord(word: Word): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("word", word.word)
            put("phonetic", word.phonetic)
            put("definition", word.definition)
            put("example", word.example)
            put("word_type", word.wordType)
            put("is_collect", if (word.isCollect) 1 else 0)
            put("is_custom", if (word.isCustom) 1 else 0)
            put("custom_book_id", word.customBookId)
        }
        return db.insert("words", null, values)
    }

    fun deleteWord(wordId: Int): Int {
        val db = dbHelper.writableDatabase
        return db.delete("words", "id = ?", arrayOf(wordId.toString()))
    }

    fun updateWord(word: Word): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("word", word.word)
            put("phonetic", word.phonetic)
            put("definition", word.definition)
            put("example", word.example)
            put("word_type", word.wordType)
            put("is_collect", if (word.isCollect) 1 else 0)
            put("is_custom", if (word.isCustom) 1 else 0)
            put("custom_book_id", word.customBookId)
        }
        return db.update("words", values, "id = ?", arrayOf(word.id.toString()))
    }

    fun searchWord(keyword: String): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val pattern = "%$keyword%"
            val cursor = db.rawQuery(
                "SELECT * FROM words WHERE word LIKE ? OR definition LIKE ?",
                arrayOf(pattern, pattern)
            )
            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun getCustomWordsByBookId(bookId: Int): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val cursor = db.rawQuery(
                "SELECT * FROM words WHERE is_custom = 1 AND custom_book_id = ?",
                arrayOf(bookId.toString())
            )
            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun getLearnedWordsByDate(category: String, customBookId: Int, learnedDate: String): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val cursor = if (customBookId > 0) {
                db.rawQuery(
                    """
                    SELECT w.* FROM words w
                    INNER JOIN learned_words lw ON lw.word_id = w.id
                    WHERE lw.learned_date = ?
                      AND w.is_custom = 1
                      AND w.custom_book_id = ?
                    ORDER BY RANDOM()
                    """.trimIndent(),
                    arrayOf(learnedDate, customBookId.toString())
                )
            } else {
                db.rawQuery(
                    """
                    SELECT w.* FROM words w
                    INNER JOIN learned_words lw ON lw.word_id = w.id
                    WHERE lw.learned_date = ?
                      AND w.is_custom = 0
                      AND w.word_type = ?
                    ORDER BY RANDOM()
                    """.trimIndent(),
                    arrayOf(learnedDate, category)
                )
            }
            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun getLearnedWordsByDate(learnedDate: String): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val cursor = db.rawQuery(
                """
                SELECT w.* FROM words w
                INNER JOIN learned_words lw ON lw.word_id = w.id
                WHERE lw.learned_date = ?
                ORDER BY w.word_type, w.word COLLATE NOCASE
                """.trimIndent(),
                arrayOf(learnedDate)
            )
            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun getAllLearnedWords(): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val cursor = db.rawQuery(
                """
                SELECT w.* FROM words w
                INNER JOIN learned_words lw ON lw.word_id = w.id
                GROUP BY w.id
                ORDER BY MAX(lw.learned_date) DESC, w.word COLLATE NOCASE
                """.trimIndent(),
                null
            )
            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun getLearnedWordsByCategory(category: String): List<Word> {
        if (category == "全部") return getAllLearnedWords()

        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val catCursor = db.rawQuery(
                "SELECT is_custom FROM word_category WHERE category_name = ? LIMIT 1",
                arrayOf(category)
            )
            val isCustom = catCursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }

            val cursor = if (isCustom == 1) {
                val customBook = CustomWordBookDao(context).getCustomWordBookByName(category)
                if (customBook == null) {
                    return emptyList()
                }
                db.rawQuery(
                    """
                    SELECT w.* FROM words w
                    INNER JOIN learned_words lw ON lw.word_id = w.id
                    WHERE w.is_custom = 1 AND w.custom_book_id = ?
                    GROUP BY w.id
                    ORDER BY MAX(lw.learned_date) DESC, w.word COLLATE NOCASE
                    """.trimIndent(),
                    arrayOf(customBook.id.toString())
                )
            } else {
                db.rawQuery(
                    """
                    SELECT w.* FROM words w
                    INNER JOIN learned_words lw ON lw.word_id = w.id
                    WHERE w.is_custom = 0 AND w.word_type = ?
                    GROUP BY w.id
                    ORDER BY MAX(lw.learned_date) DESC, w.word COLLATE NOCASE
                    """.trimIndent(),
                    arrayOf(category)
                )
            }

            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun getCollectedWords(): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val cursor = db.rawQuery("SELECT * FROM words WHERE is_collect = 1", null)
            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun toggleCollect(wordId: Int, collect: Boolean): Int {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("is_collect", if (collect) 1 else 0)
        }
        return db.update("words", values, "id = ?", arrayOf(wordId.toString()))
    }

    fun getWordById(wordId: Int): Word? {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM words WHERE id = ?", arrayOf(wordId.toString()))
        return cursor.use {
            if (it.moveToFirst()) it.toWord() else null
        }
    }

    fun getAllCategories(): List<String> {
        val db = dbHelper.readableDatabase
        val categories = mutableListOf<String>()
        try {
            val cursor = db.rawQuery(
                "SELECT DISTINCT category_name FROM word_category ORDER BY category_name",
                null
            )
            cursor.use {
                while (it.moveToNext()) {
                    categories.add(it.getString(0))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return categories
    }

    fun getWordCountByCategory(category: String): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM words WHERE word_type = ? AND is_custom = 0",
            arrayOf(category)
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun getWordsPaged(
        category: String,
        customBookId: Int,
        keyword: String?,
        limit: Int,
        offset: Int
    ): List<Word> {
        val db = dbHelper.readableDatabase
        val words = mutableListOf<Word>()
        try {
            val whereParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            buildCategoryFilter(db, category, customBookId, whereParts, args)

            val normalizedKeyword = keyword?.trim().orEmpty()
            if (normalizedKeyword.isNotBlank()) {
                whereParts.add("(word LIKE ? OR definition LIKE ?)")
                args.add("%$normalizedKeyword%")
                args.add("%$normalizedKeyword%")
            }

            val whereClause = if (whereParts.isEmpty()) "1=1" else whereParts.joinToString(" AND ")
            val sql = """
                SELECT * FROM words
                WHERE $whereClause
                ORDER BY id DESC
                LIMIT ? OFFSET ?
            """.trimIndent()
            args.add(limit.toString())
            args.add(offset.toString())

            val cursor = db.rawQuery(sql, args.toTypedArray())
            cursor.use { it.toWordList(words) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    fun countWords(category: String, customBookId: Int, keyword: String?): Int {
        val db = dbHelper.readableDatabase
        return try {
            val whereParts = mutableListOf<String>()
            val args = mutableListOf<String>()

            buildCategoryFilter(db, category, customBookId, whereParts, args)

            val normalizedKeyword = keyword?.trim().orEmpty()
            if (normalizedKeyword.isNotBlank()) {
                whereParts.add("(word LIKE ? OR definition LIKE ?)")
                args.add("%$normalizedKeyword%")
                args.add("%$normalizedKeyword%")
            }

            val whereClause = if (whereParts.isEmpty()) "1=1" else whereParts.joinToString(" AND ")
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM words WHERE $whereClause",
                args.toTypedArray()
            )
            cursor.use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    private fun buildCategoryFilter(
        db: android.database.sqlite.SQLiteDatabase,
        category: String,
        customBookId: Int,
        whereParts: MutableList<String>,
        args: MutableList<String>
    ) {
        if (customBookId != -1) {
            whereParts.add("is_custom = 1 AND custom_book_id = ?")
            args.add(customBookId.toString())
            return
        }

        if (category == "全部") return

        val catCursor = db.rawQuery(
            "SELECT is_custom FROM word_category WHERE category_name = ? LIMIT 1",
            arrayOf(category)
        )
        val isCustom = catCursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

        if (isCustom == 1) {
            val customBook = CustomWordBookDao(context).getCustomWordBookByName(category)
            if (customBook != null) {
                whereParts.add("is_custom = 1 AND custom_book_id = ?")
                args.add(customBook.id.toString())
            } else {
                whereParts.add("1 = 0")
            }
        } else {
            whereParts.add("is_custom = 0 AND word_type = ?")
            args.add(category)
        }
    }

    private fun Cursor.toWordList(list: MutableList<Word>) {
        while (moveToNext()) {
            list.add(toWord())
        }
    }

    private fun Cursor.toWord(): Word {
        return Word(
            id = getInt(getColumnIndexOrThrow("id")),
            word = getString(getColumnIndexOrThrow("word")),
            phonetic = getStringOrNull("phonetic"),
            definition = getString(getColumnIndexOrThrow("definition")),
            example = getStringOrNull("example"),
            wordType = getStringOrNull("word_type") ?: "",
            isCollect = getInt(getColumnIndexOrThrow("is_collect")) == 1,
            isCustom = getInt(getColumnIndexOrThrow("is_custom")) == 1,
            customBookId = getInt(getColumnIndexOrThrow("custom_book_id")),
            createdAt = getStringOrNull("created_at") ?: ""
        )
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }
}
