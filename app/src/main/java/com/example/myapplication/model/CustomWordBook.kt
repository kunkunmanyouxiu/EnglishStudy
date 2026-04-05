package com.example.myapplication.model

data class CustomWordBook(
    val id: Int = 0,
    val bookName: String,
    val createTime: String = "",
    val wordCount: Int = 0,
    val isDelete: Boolean = false
)
