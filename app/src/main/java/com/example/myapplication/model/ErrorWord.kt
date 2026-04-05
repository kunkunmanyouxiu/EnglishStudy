package com.example.myapplication.model

data class ErrorWord(
    val id: Int = 0,
    val wordId: Int,
    val errorCount: Int = 1,
    val lastErrorTime: String = "",
    val errorAnswer: String = "",
    // 关联单词信息（Join查询时填充）
    val word: String = "",
    val definition: String = "",
    val phonetic: String? = null
)
