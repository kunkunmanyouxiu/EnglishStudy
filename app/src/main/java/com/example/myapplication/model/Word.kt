package com.example.myapplication.model

data class Word(
    val id: Int = 0,
    val word: String,
    val phonetic: String? = null,
    val definition: String,
    val example: String? = null,
    val wordType: String = "四级",
    val isCollect: Boolean = false,
    val isCustom: Boolean = false,
    val customBookId: Int = 0,
    val createdAt: String = ""
)
