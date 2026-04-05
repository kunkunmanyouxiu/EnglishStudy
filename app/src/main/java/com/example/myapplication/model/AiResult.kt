package com.example.myapplication.model

// AI 判题结果
data class JudgeResult(
    val result: String,       // 正确 / 部分正确 / 错误
    val definition: String,
    val hint: String = ""
)

// AI 预判断结果
data class PreJudgeResult(
    val matchLevel: String,   // 高 / 中 / 低
    val matchExplanation: String,
    val optimizationSuggestion: String = ""
)
