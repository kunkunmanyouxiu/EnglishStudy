package com.example.myapplication.algorithm

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object EbbinghausAlgorithm {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // 状态常量
    const val STATUS_UNKNOWN = 0   // 不会
    const val STATUS_FUZZY = 1     // 模糊
    const val STATUS_MASTERED = 2  // 掌握

    /**
     * 根据当前状态和复习次数计算下次复习时间
     */
    fun calculateNextReviewTime(status: Int, reviewCount: Int): String {
        val now = Calendar.getInstance()
        val intervals = getIntervals(status)
        val index = reviewCount.coerceAtMost(intervals.size - 1)
        val intervalMinutes = intervals[index]
        now.add(Calendar.MINUTE, intervalMinutes)
        return dateFormat.format(now.time)
    }

    /**
     * 提升状态（答对时）
     */
    fun upgradeStatus(currentStatus: Int): Int {
        return when (currentStatus) {
            STATUS_UNKNOWN -> STATUS_FUZZY
            STATUS_FUZZY -> STATUS_MASTERED
            else -> STATUS_MASTERED
        }
    }

    /**
     * 降低状态（答错时）
     */
    fun downgradeStatus(currentStatus: Int): Int {
        return when (currentStatus) {
            STATUS_MASTERED -> STATUS_FUZZY
            STATUS_FUZZY -> STATUS_UNKNOWN
            else -> STATUS_UNKNOWN
        }
    }

    /**
     * 根据判题结果映射到状态
     * 正确 -> 提升状态
     * 部分正确 -> 维持/设置为FUZZY
     * 错误 -> 降低状态
     */
    fun judgeResultToStatus(judgeResult: String, currentStatus: Int): Int {
        return when (judgeResult) {
            "正确" -> upgradeStatus(currentStatus)
            "部分正确" -> STATUS_FUZZY
            else -> STATUS_UNKNOWN
        }
    }

    /**
     * 获取各状态的复习间隔（分钟）
     */
    private fun getIntervals(status: Int): IntArray {
        return when (status) {
            STATUS_UNKNOWN -> intArrayOf(
                60,          // 1小时
                60 * 24,     // 1天
                60 * 24 * 3, // 3天
                60 * 24 * 7, // 7天
                60 * 24 * 14,// 14天
                60 * 24 * 30 // 30天
            )
            STATUS_FUZZY -> intArrayOf(
                60 * 12,     // 12小时
                60 * 24 * 2, // 2天
                60 * 24 * 5, // 5天
                60 * 24 * 10,// 10天
                60 * 24 * 20 // 20天
            )
            STATUS_MASTERED -> intArrayOf(
                60 * 24 * 7, // 7天
                60 * 24 * 14,// 14天
                60 * 24 * 30,// 30天
                60 * 24 * 60 // 60天
            )
            else -> intArrayOf(60)
        }
    }

    fun formatDate(dateStr: String): String {
        return try {
            val date = dateFormat.parse(dateStr) ?: return dateStr
            val now = System.currentTimeMillis()
            val diff = date.time - now
            when {
                diff < 0 -> "已到期"
                diff < 60 * 60 * 1000 -> "${diff / 60000}分钟后"
                diff < 24 * 60 * 60 * 1000 -> "${diff / 3600000}小时后"
                else -> "${diff / (24 * 3600000)}天后"
            }
        } catch (e: Exception) {
            dateStr
        }
    }
}
