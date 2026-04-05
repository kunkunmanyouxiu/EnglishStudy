package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.WordAdapter
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityWordLibraryBinding
import com.example.myapplication.model.Word
import kotlinx.coroutines.*

class WordLibraryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWordLibraryBinding
    private val wordDao by lazy { WordDao(this) }
    private lateinit var adapter: WordAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentCategory = "全部" // 默认显示全部
    private var customBookId: Int = -1 // 自定义词库ID，-1表示未指定

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWordLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 读取Intent参数：自定义词库ID和名称
        customBookId = intent.getIntExtra("custom_book_id", -1)
        val customBookName = intent.getStringExtra("custom_book_name")
        if (customBookId != -1 && !customBookName.isNullOrBlank()) {
            currentCategory = customBookName
        }

        adapter = WordAdapter()
        setupRecyclerView()
        setupCategoryChips()
        setupSearch()
        setupButtons()
        loadWords()
    }

    private fun setupRecyclerView() {
        binding.rvWords.layoutManager = LinearLayoutManager(this)
        binding.rvWords.adapter = adapter
    }

    private fun setupCategoryChips() {
        scope.launch(Dispatchers.IO) {
            val categories = wordDao.getAllCategories()
            withContext(Dispatchers.Main) {
                binding.chipGroupCategory.removeAllViews()

                // 添加"全部"选项
                val allChip = com.google.android.material.chip.Chip(this@WordLibraryActivity).apply {
                    text = "全部"
                    isCheckable = true
                    isChecked = currentCategory == "全部"
                    setOnClickListener {
                        onCategorySelected("全部")
                    }
                }
                binding.chipGroupCategory.addView(allChip)

                // 添加分类选项
                categories.forEach { cat ->
                    val chip = com.google.android.material.chip.Chip(this@WordLibraryActivity).apply {
                        text = cat
                        isCheckable = true
                        isChecked = cat == currentCategory
                        setOnClickListener {
                            onCategorySelected(cat)
                        }
                    }
                    binding.chipGroupCategory.addView(chip)
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString() ?: ""
                if (keyword.isBlank()) {
                    loadWords()
                } else {
                    searchWords(keyword)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupButtons() {
        binding.btnCustomUpload.setOnClickListener {
            startActivity(Intent(this, CustomWordUploadActivity::class.java))
        }
        binding.btnCustomBooks.setOnClickListener {
            startActivity(Intent(this, CustomBookManagerActivity::class.java))
        }
        binding.fabAddWord.setOnClickListener {
            startActivity(Intent(this, CustomWordUploadActivity::class.java))
        }
    }

    private fun loadWords() {
        scope.launch(Dispatchers.IO) {
            val words = when {
                customBookId != -1 -> wordDao.getCustomWordsByBookId(customBookId)
                currentCategory == "全部" -> wordDao.getAllWords()
                else -> wordDao.getAllWordsByCategory(currentCategory)
            }
            withContext(Dispatchers.Main) {
                adapter.submitList(words)
                binding.tvWordCount.text = "共 ${words.size} 个单词"
            }
        }
    }

    private fun onCategorySelected(category: String) {
        currentCategory = category
        customBookId = -1  // 切换分类时清除词库ID限制
        loadWords()
    }

    private fun searchWords(keyword: String) {
        scope.launch(Dispatchers.IO) {
            val words = wordDao.searchWord(keyword)
            withContext(Dispatchers.Main) {
                adapter.submitList(words)
                binding.tvWordCount.text = "搜索到 ${words.size} 个单词"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 重新加载分类按钮和单词列表（以便显示新创建的自定义词库）
        setupCategoryChips()
        loadWords()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
