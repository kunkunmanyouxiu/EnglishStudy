package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.database.StudyRecordDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityReviewBinding
import kotlinx.coroutines.*

class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewBinding
    private val studyRecordDao by lazy { StudyRecordDao(this) }
    private val wordDao by lazy { WordDao(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadReviewWords()

        binding.btnStartReview.setOnClickListener {
            startReviewDictation()
        }
    }

    private fun loadReviewWords() {
        scope.launch(Dispatchers.IO) {
            val reviewWordIds = studyRecordDao.getTodayReviewWords()
            withContext(Dispatchers.Main) {
                if (reviewWordIds.isEmpty()) {
                    binding.tvReviewCount.text = "今日无需复习，继续保持！"
                    binding.btnStartReview.isEnabled = false
                } else {
                    binding.tvReviewCount.text = "今日待复习：${reviewWordIds.size} 个单词"
                    binding.btnStartReview.isEnabled = true
                }
            }
        }
    }

    private fun startReviewDictation() {
        scope.launch(Dispatchers.IO) {
            val reviewWordIds = studyRecordDao.getTodayReviewWords()
            if (reviewWordIds.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReviewActivity, "今日无需复习", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                val intent = Intent(this@ReviewActivity, DictationActivity::class.java).apply {
                    putExtra(DictationActivity.EXTRA_MODE, DictationActivity.MODE_EN_TO_CN)
                    putExtra(DictationActivity.EXTRA_CATEGORY, "复习")
                }
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadReviewWords()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
