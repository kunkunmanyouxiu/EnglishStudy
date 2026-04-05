package com.example.myapplication.model

data class StudyRecord(
    val id: Int = 0,
    val wordId: Int,
    val status: Int = 0, // 0=不会, 1=模糊, 2=掌握
    val reviewCount: Int = 0,
    val lastReviewTime: String = "",
    val nextReviewTime: String = ""
)
