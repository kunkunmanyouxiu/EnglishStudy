package com.example.myapplication.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "english_study.db"
        const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_WORDS)
        db.execSQL(SQL_CREATE_STUDY_RECORDS)
        db.execSQL(SQL_CREATE_ERROR_WORDS)
        db.execSQL(SQL_CREATE_WORD_CATEGORY)
        db.execSQL(SQL_CREATE_CUSTOM_WORD_BOOK)
        insertDefaultCategories(db)
        // insertSampleWords(db) // 注释掉，不插入示例数据
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS words")
        db.execSQL("DROP TABLE IF EXISTS study_records")
        db.execSQL("DROP TABLE IF EXISTS error_words")
        db.execSQL("DROP TABLE IF EXISTS word_category")
        db.execSQL("DROP TABLE IF EXISTS custom_word_book")
        onCreate(db)
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

    private fun insertDefaultCategories(db: SQLiteDatabase) {
        val categories = listOf("四级", "六级", "考研", "雅思", "托福")
        categories.forEach { name ->
            db.execSQL("INSERT INTO word_category (category_name, is_custom) VALUES ('$name', 0)")
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
