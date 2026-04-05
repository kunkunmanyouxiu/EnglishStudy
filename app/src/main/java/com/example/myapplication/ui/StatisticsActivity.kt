package com.example.myapplication.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.database.CustomWordBookDao
import com.example.myapplication.database.ErrorWordDao
import com.example.myapplication.database.StudyRecordDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityStatisticsBinding
import kotlinx.coroutines.*

class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private val wordDao by lazy { WordDao(this) }
    private val studyRecordDao by lazy { StudyRecordDao(this) }
    private val errorWordDao by lazy { ErrorWordDao(this) }
    private val customBookDao by lazy { CustomWordBookDao(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadStats()
    }

    private fun loadStats() {
        scope.launch(Dispatchers.IO) {
            val totalWords = wordDao.getAllWords().size
            val customWordCount = customBookDao.getTotalCustomWordCount()
            val customBookCount = customBookDao.getAllCustomWordBooks().size
            val totalStudied = studyRecordDao.getTotalStudiedCount()
            val todayReview = studyRecordDao.getTodayReviewCount()
            val totalErrors = errorWordDao.getTotalErrorCount()
            val (correct, partial, wrong) = studyRecordDao.getTodayStudyStats()

            withContext(Dispatchers.Main) {
                binding.tvTotalWords.text = "词库总量：$totalWords 词"
                binding.tvCustomWords.text = "自定义单词：$customWordCount 词（$customBookCount 个词库）"
                binding.tvTotalStudied.text = "累计学习：$totalStudied 次"
                binding.tvTodayReview.text = "今日已复习：$todayReview 词"
                binding.tvTotalErrors.text = "错题总数：$totalErrors 词"
                binding.tvTodayStats.text = "今日 — 正确：$correct  部分：$partial  错误：$wrong"

                // 简单柱状图（自绘）
                drawBarChart(correct, partial, wrong)
            }
        }
    }

    private fun drawBarChart(correct: Int, partial: Int, wrong: Int) {
        val chartView = binding.chartView
        val total = (correct + partial + wrong).coerceAtLeast(1)

        // 先设为 INVISIBLE（占位但不显示），让布局计算出正确尺寸
        chartView.visibility = View.INVISIBLE
        chartView.post {
            val w = chartView.width.toFloat()
            val h = chartView.height.toFloat()
            if (w <= 0 || h <= 0) {
                chartView.visibility = View.GONE
                return@post
            }
            val bitmap = android.graphics.Bitmap.createBitmap(w.toInt(), h.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val barWidth = w / 5
            val maxBarH = h * 0.8f

            // 正确 - 绿色
            paint.color = Color.parseColor("#4CAF50")
            val correctH = (correct.toFloat() / total) * maxBarH
            canvas.drawRect(barWidth * 0.5f, h - correctH - 40, barWidth * 1.5f, h - 40f, paint)

            // 部分正确 - 橙色
            paint.color = Color.parseColor("#FF9800")
            val partialH = (partial.toFloat() / total) * maxBarH
            canvas.drawRect(barWidth * 2f, h - partialH - 40, barWidth * 3f, h - 40f, paint)

            // 错误 - 红色
            paint.color = Color.parseColor("#F44336")
            val wrongH = (wrong.toFloat() / total) * maxBarH
            canvas.drawRect(barWidth * 3.5f, h - wrongH - 40, barWidth * 4.5f, h - 40f, paint)

            // 标签
            paint.color = Color.DKGRAY
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("正确", barWidth, h - 10f, paint)
            canvas.drawText("部分", barWidth * 2.5f, h - 10f, paint)
            canvas.drawText("错误", barWidth * 4f, h - 10f, paint)

            chartView.setImageBitmap(bitmap)
            chartView.visibility = View.VISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
