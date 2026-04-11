package com.example.myapplication.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.WordAdapter
import com.example.myapplication.database.LearnedWordDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityLearnedWordsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LearnedWordsActivity : AppCompatActivity() {

    private enum class Mode { BY_DATE, BY_CATEGORY }

    private lateinit var binding: ActivityLearnedWordsBinding
    private val wordDao by lazy { WordDao(this) }
    private val learnedWordDao by lazy { LearnedWordDao(this) }
    private val adapter by lazy { WordAdapter() }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mode: Mode = Mode.BY_DATE
    private var selectedDate: String = ""
    private var selectedCategory: String = "全部"
    private var availableDates: List<String> = emptyList()
    private var availableCategories: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLearnedWordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvLearnedWords.layoutManager = LinearLayoutManager(this)
        binding.rvLearnedWords.adapter = adapter

        setupButtons()
        initializeFiltersAndLoad()
    }

    private fun setupButtons() {
        binding.btnModeByDate.setOnClickListener {
            if (mode != Mode.BY_DATE) {
                mode = Mode.BY_DATE
                updateModeUI()
                loadLearnedWords()
            }
        }

        binding.btnModeByCategory.setOnClickListener {
            if (mode != Mode.BY_CATEGORY) {
                mode = Mode.BY_CATEGORY
                updateModeUI()
                loadLearnedWords()
            }
        }

        binding.btnSelectFilter.setOnClickListener {
            when (mode) {
                Mode.BY_DATE -> showDatePickerDialog()
                Mode.BY_CATEGORY -> showCategoryPickerDialog()
            }
        }
    }

    private fun initializeFiltersAndLoad() {
        scope.launch(Dispatchers.IO) {
            availableDates = learnedWordDao.getLearnedDates()
            availableCategories = listOf("全部") + wordDao.getAllCategories()
            if (selectedDate.isBlank() && availableDates.isNotEmpty()) {
                selectedDate = availableDates.first()
            }
            withContext(Dispatchers.Main) {
                updateModeUI()
                loadLearnedWords()
            }
        }
    }

    private fun updateModeUI() {
        if (mode == Mode.BY_DATE) {
            binding.btnModeByDate.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1976D2.toInt())
            binding.btnModeByCategory.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF90A4AE.toInt())
            binding.tvCurrentFilter.text = if (selectedDate.isBlank()) {
                "筛选：暂无学习记录日期"
            } else {
                "筛选日期：$selectedDate"
            }
            binding.btnSelectFilter.text = "选择日期"
        } else {
            binding.btnModeByDate.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF90A4AE.toInt())
            binding.btnModeByCategory.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1976D2.toInt())
            binding.tvCurrentFilter.text = "筛选词库：$selectedCategory"
            binding.btnSelectFilter.text = "选择词库"
        }
    }

    private fun loadLearnedWords() {
        scope.launch(Dispatchers.IO) {
            val words = when (mode) {
                Mode.BY_DATE -> {
                    if (selectedDate.isBlank()) emptyList()
                    else wordDao.getLearnedWordsByDate(selectedDate)
                }
                Mode.BY_CATEGORY -> wordDao.getLearnedWordsByCategory(selectedCategory)
            }

            withContext(Dispatchers.Main) {
                adapter.submitList(words)
                binding.tvCount.text = "共 ${words.size} 个已学习单词"
            }
        }
    }

    private fun showDatePickerDialog() {
        if (availableDates.isEmpty()) {
            Toast.makeText(this, "暂无学习记录日期", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("选择学习日期")
            .setItems(availableDates.toTypedArray()) { _, which ->
                selectedDate = availableDates[which]
                updateModeUI()
                loadLearnedWords()
            }
            .show()
    }

    private fun showCategoryPickerDialog() {
        if (availableCategories.isEmpty()) {
            Toast.makeText(this, "暂无词库分类", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("选择词库分类")
            .setItems(availableCategories.toTypedArray()) { _, which ->
                selectedCategory = availableCategories[which]
                updateModeUI()
                loadLearnedWords()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        initializeFiltersAndLoad()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

