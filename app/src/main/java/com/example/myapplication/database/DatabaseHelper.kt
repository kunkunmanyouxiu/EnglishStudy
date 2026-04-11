package com.example.myapplication.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.nio.charset.Charset

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext = context.applicationContext

    companion object {
        const val DATABASE_NAME = "english_study.db"
        const val DATABASE_VERSION = 3
        private const val TAG = "DatabaseHelper"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_WORDS)
        db.execSQL(SQL_CREATE_STUDY_RECORDS)
        db.execSQL(SQL_CREATE_ERROR_WORDS)
        db.execSQL(SQL_CREATE_WORD_CATEGORY)
        db.execSQL(SQL_CREATE_CUSTOM_WORD_BOOK)
        db.execSQL(SQL_CREATE_LEARNED_WORDS)
        ensureBuiltInCategories(db)
        importBuiltInWordBooks(db)
        // insertSampleWords(db) // 注释掉，不插入示例数据
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(SQL_CREATE_LEARNED_WORDS)
        }
        if (oldVersion < 3) {
            ensureBuiltInCategories(db)
            importBuiltInWordBooks(db)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    private val SQL_CREATE_WORDS = """
        CREATE TABLE IF NOT EXISTS words (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            word TEXT NOT NULL,
            phonetic TEXT,
            definition TEXT NOT NULL,
            example TEXT,
            word_type TEXT,
            is_collect INTEGER DEFAULT 0,
            is_custom INTEGER DEFAULT 0,
            custom_book_id INTEGER DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """.trimIndent()

    private val SQL_CREATE_STUDY_RECORDS = """
        CREATE TABLE IF NOT EXISTS study_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            word_id INTEGER NOT NULL,
            status INTEGER NOT NULL DEFAULT 0,
            review_count INTEGER DEFAULT 0,
            last_review_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            next_review_time TIMESTAMP NOT NULL,
            FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
        )
    """.trimIndent()

    private val SQL_CREATE_ERROR_WORDS = """
        CREATE TABLE IF NOT EXISTS error_words (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            word_id INTEGER NOT NULL,
            error_count INTEGER DEFAULT 1,
            last_error_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            error_answer TEXT,
            FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
        )
    """.trimIndent()

    private val SQL_CREATE_WORD_CATEGORY = """
        CREATE TABLE IF NOT EXISTS word_category (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            category_name TEXT NOT NULL,
            word_count INTEGER DEFAULT 0,
            is_custom INTEGER DEFAULT 0
        )
    """.trimIndent()

    private val SQL_CREATE_CUSTOM_WORD_BOOK = """
        CREATE TABLE IF NOT EXISTS custom_word_book (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_name TEXT NOT NULL,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            word_count INTEGER DEFAULT 0,
            is_delete INTEGER DEFAULT 0
        )
    """.trimIndent()

    private val SQL_CREATE_LEARNED_WORDS = """
        CREATE TABLE IF NOT EXISTS learned_words (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            word_id INTEGER NOT NULL,
            learned_date TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(word_id, learned_date),
            FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
        )
    """.trimIndent()

    private fun ensureBuiltInCategories(db: SQLiteDatabase) {
        // 旧版本分类迁移
        db.execSQL("UPDATE words SET word_type = '考研英语' WHERE word_type = '考研' AND is_custom = 0")
        db.execSQL("UPDATE word_category SET category_name = '考研英语' WHERE category_name = '考研'")
        db.execSQL(
            """
            DELETE FROM word_category
            WHERE category_name = '雅思'
              AND is_custom = 0
              AND NOT EXISTS (
                  SELECT 1 FROM words WHERE word_type = '雅思' AND is_custom = 0
              )
            """.trimIndent()
        )
        db.execSQL("DELETE FROM word_category WHERE category_name = '考研'")

        val categories = listOf("四级", "六级", "考研英语", "SAT", "托福", "初中英语", "高中英语")
        categories.forEach { name ->
            val cursor = db.rawQuery(
                "SELECT 1 FROM word_category WHERE category_name = ? LIMIT 1",
                arrayOf(name)
            )
            val exists = cursor.use { it.moveToFirst() }
            if (!exists) {
                db.execSQL(
                    "INSERT INTO word_category (category_name, is_custom) VALUES (?, 0)",
                    arrayOf(name)
                )
            }
        }
    }

    private fun importBuiltInWordBooks(db: SQLiteDatabase) {
        val builtinFiles = listOf(
            "cet4.csv" to "四级",
            "cet6.csv" to "六级",
            "graduate.csv" to "考研英语",
            "sat.csv" to "SAT",
            "toefl.csv" to "托福",
            "junior.csv" to "初中英语",
            "senior.csv" to "高中英语"
        )

        db.beginTransaction()
        try {
            var totalInserted = 0
            builtinFiles.forEach { (fileName, category) ->
                totalInserted += importOneCsv(db, fileName, category)
            }
            db.setTransactionSuccessful()
            Log.i(TAG, "内置词库导入完成，新增 $totalInserted 个单词")
        } catch (e: Exception) {
            Log.e(TAG, "导入内置词库失败: ${e.message}", e)
        } finally {
            db.endTransaction()
        }
    }

    private fun importOneCsv(db: SQLiteDatabase, fileName: String, category: String): Int {
        val content = readAssetCsv(fileName) ?: return 0
        if (content.isBlank()) return 0

        var inserted = 0
        val lines = content
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        lines.forEachIndexed { index, line ->
            if (index == 0 && line.startsWith("word", ignoreCase = true)) return@forEachIndexed

            val firstComma = line.indexOf(',')
            if (firstComma <= 0 || firstComma >= line.lastIndex) return@forEachIndexed

            val word = line.substring(0, firstComma).trim().trim('"')
            val definitionRaw = line.substring(firstComma + 1).trim()
            val definition = definitionRaw.trim().trim('"')

            if (word.isBlank() || definition.isBlank()) return@forEachIndexed

            val existsCursor = db.rawQuery(
                """
                SELECT 1 FROM words
                WHERE word = ?
                  AND definition = ?
                  AND word_type = ?
                  AND is_custom = 0
                LIMIT 1
                """.trimIndent(),
                arrayOf(word, definition, category)
            )
            val exists = existsCursor.use { it.moveToFirst() }
            if (exists) return@forEachIndexed

            db.execSQL(
                """
                INSERT INTO words
                (word, phonetic, definition, example, word_type, is_collect, is_custom, custom_book_id)
                VALUES (?, ?, ?, ?, ?, 0, 0, 0)
                """.trimIndent(),
                arrayOf(word, "", definition, "", category)
            )
            inserted++
        }
        return inserted
    }

    private fun readAssetCsv(fileName: String): String? {
        return try {
            val bytes = appContext.assets.open(fileName).use { it.readBytes() }

            // 优先 UTF-8，如果出现大量替换字符则回退 GBK
            val utf8 = bytes.toString(Charsets.UTF_8)
            if (!utf8.contains("�")) {
                utf8
            } else {
                bytes.toString(Charset.forName("GBK"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取内置词库失败: $fileName, ${e.message}")
            null
        }
    }

    private fun insertSampleWords(db: SQLiteDatabase) {
        val sampleWords = listOf(
            Triple("abandon", "[əˈbændən]", "放弃；遗弃"),
            Triple("ability", "[əˈbɪlɪtɪ]", "能力；才能"),
            Triple("absence", "[ˈæbsəns]", "缺席；缺乏"),
            Triple("absolute", "[ˈæbsəluːt]", "绝对的；完全的"),
            Triple("abstract", "[ˈæbstrækt]", "抽象的；摘要"),
            Triple("academic", "[ˌækəˈdemɪk]", "学术的；学业的"),
            Triple("accelerate", "[əkˈseləreɪt]", "加速；促进"),
            Triple("accomplish", "[əˈkʌmplɪʃ]", "完成；实现"),
            Triple("accurate", "[ˈækjərɪt]", "准确的；精确的"),
            Triple("achieve", "[əˈtʃiːv]", "实现；达到"),
            Triple("acknowledge", "[əkˈnɒlɪdʒ]", "承认；致谢"),
            Triple("acquire", "[əˈkwaɪər]", "获得；习得"),
            Triple("adapt", "[əˈdæpt]", "适应；改编"),
            Triple("adequate", "[ˈædɪkwɪt]", "足够的；适当的"),
            Triple("analyze", "[ˈænəlaɪz]", "分析；解析"),
            Triple("approach", "[əˈprəʊtʃ]", "方法；接近"),
            Triple("appreciate", "[əˈpriːʃɪeɪt]", "欣赏；感激"),
            Triple("appropriate", "[əˈprəʊprɪɪt]", "适当的；合适的"),
            Triple("assume", "[əˈsjuːm]", "假设；承担"),
            Triple("attitude", "[ˈætɪtjuːd]", "态度；看法"),
            Triple("benefit", "[ˈbenɪfɪt]", "利益；好处；受益"),
            Triple("capable", "[ˈkeɪpəbl]", "有能力的；能干的"),
            Triple("challenge", "[ˈtʃælɪndʒ]", "挑战；质疑"),
            Triple("circumstance", "[ˈsɜːkəmstæns]", "情况；环境；境遇"),
            Triple("communicate", "[kəˈmjuːnɪkeɪt]", "交流；传达"),
            Triple("complex", "[ˈkɒmpleks]", "复杂的；综合体"),
            Triple("concentrate", "[ˈkɒnsəntreɪt]", "集中；专心"),
            Triple("consider", "[kənˈsɪdər]", "考虑；认为"),
            Triple("contribute", "[kənˈtrɪbjuːt]", "贡献；促成"),
            Triple("crucial", "[ˈkruːʃl]", "至关重要的；决定性的")
        )

        sampleWords.forEach { (word, phonetic, definition) ->
            db.execSQL(
                "INSERT INTO words (word, phonetic, definition, word_type) VALUES (?, ?, ?, '四级')",
                arrayOf(word, phonetic, definition)
            )
        }
    }
}
