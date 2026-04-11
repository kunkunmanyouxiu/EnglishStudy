package com.example.myapplication.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.LearnWordAdapter
import com.example.myapplication.database.LearnedWordDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityLearnWordsBinding
import com.example.myapplication.model.Word
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class LearnWordsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_CUSTOM_BOOK_ID = "extra_custom_book_id"
        const val EXTRA_LEARN_COUNT = "extra_learn_count"
        private const val PAGE_SIZE = 10
    }

    private lateinit var binding: ActivityLearnWordsBinding
    private val wordDao by lazy { WordDao(this) }
    private val learnedWordDao by lazy { LearnedWordDao(this) }
    private val adapter by lazy { LearnWordAdapter() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var category = "四级"
    private var customBookId = 0
    private var learnCount = 0
    private val learnDate = LocalDate.now().toString()

    private var pages: List<List<Word>> = emptyList()
    private var currentPageIndex = 0
    private var learnedCount = 0
    private var maskWord = false
    private var maskDefinition = false
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearnWordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        category = intent.getStringExtra(EXTRA_CATEGORY) ?: "四级"
        customBookId = intent.getIntExtra(EXTRA_CUSTOM_BOOK_ID, 0)
        learnCount = intent.getIntExtra(EXTRA_LEARN_COUNT, 0)

        binding.rvWords.layoutManager = LinearLayoutManager(this)
        binding.rvWords.adapter = adapter

        setupButtons()
        loadWords()
    }

    private fun setupButtons() {
        binding.btnToggleWord.setOnClickListener {
            maskWord = !maskWord
            updateMaskState()
        }

        binding.btnToggleDefinition.setOnClickListener {
            maskDefinition = !maskDefinition
            updateMaskState()
        }

        binding.btnNextPage.setOnClickListener { saveCurrentPageAndNext() }
    }

    private fun loadWords() {
        scope.launch(Dispatchers.IO) {
            val sourceWords = if (customBookId > 0) {
                wordDao.getCustomWordsByBookId(customBookId)
            } else {
                wordDao.getAllWordsByCategory(category)
            }

            val selectedWords = sourceWords.shuffled().let { shuffled ->
                if (learnCount > 0) shuffled.take(minOf(learnCount, shuffled.size)) else shuffled
            }

            pages = selectedWords.chunked(PAGE_SIZE)
            currentPageIndex = 0
            learnedCount = 0

            withContext(Dispatchers.Main) {
                if (pages.isEmpty()) {
                    Toast.makeText(this@LearnWordsActivity, "该词库暂无可学习单词", Toast.LENGTH_SHORT).show()
                    finish()
                    return@withContext
                }
                showPage()
                updateMaskState()
            }
        }
    }

    private fun showPage() {
        val pageWords = pages.getOrNull(currentPageIndex).orEmpty()
        adapter.submitWords(pageWords)
        binding.tvHeader.text = "词库：$category  学习日期：$learnDate"
        binding.tvPageInfo.text = "第 ${currentPageIndex + 1} / ${pages.size} 页（每页 10 个）"
        binding.tvCurrentBatch.text = "本页单词：${pageWords.size}，已学习：$learnedCount 个"
        binding.btnNextPage.text = if (currentPageIndex == pages.lastIndex) {
            "完成并标记已学"
        } else {
            "下一页并标记已学"
        }
    }

    private fun updateMaskState() {
        binding.btnToggleWord.text = if (maskWord) "显示左侧单词" else "遮挡左侧单词"
        binding.btnToggleDefinition.text = if (maskDefinition) "显示右侧释义" else "遮挡右侧释义"
        adapter.updateMaskState(maskWord, maskDefinition)
    }

    private fun saveCurrentPageAndNext() {
        if (isSaving) return
        val pageWords = pages.getOrNull(currentPageIndex).orEmpty()
        if (pageWords.isEmpty()) return

        isSaving = true
        binding.btnNextPage.isEnabled = false

        scope.launch(Dispatchers.IO) {
            learnedWordDao.markWordsLearned(pageWords.map { it.id }, learnDate)
            withContext(Dispatchers.Main) {
                learnedCount += pageWords.size
                isSaving = false
                binding.btnNextPage.isEnabled = true
                if (currentPageIndex < pages.lastIndex) {
                    currentPageIndex++
                    showPage()
                    updateMaskState()
                } else {
                    showFinishDialog()
                }
            }
        }
    }

    private fun showFinishDialog() {
        AlertDialog.Builder(this)
            .setTitle("学习完成")
            .setMessage("已学习 $learnedCount 个单词\n日期：$learnDate")
            .setPositiveButton("返回首页") { _, _ -> finish() }
            .setNegativeButton("再学一轮") { _, _ ->
                maskWord = false
                maskDefinition = false
                loadWords()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

