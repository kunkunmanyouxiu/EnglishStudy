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
    private var searchJob: Job? = null
    private var currentCategory = "全部" // 默认显示全部
    private var customBookId: Int = -1 // 自定义词库ID，-1表示未指定
    private var currentKeyword: String = ""
    private val loadedWords = mutableListOf<Word>()
    private var offset = 0
    private var totalCount = 0
    private var isLoading = false
    private var hasMore = true

    companion object {
        private const val PAGE_SIZE = 120
    }

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
        val layoutManager = LinearLayoutManager(this)
        binding.rvWords.layoutManager = layoutManager
        binding.rvWords.adapter = adapter
        binding.rvWords.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || isLoading || !hasMore) return
                val last = layoutManager.findLastVisibleItemPosition()
                if (last >= adapter.itemCount - 8) {
                    loadPage(reset = false)
                }
            }
        })
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
                val keyword = s?.toString()?.trim().orEmpty()
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(250)
                    currentKeyword = keyword
                    loadPage(reset = true)
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
        loadPage(reset = true)
    }

    private fun onCategorySelected(category: String) {
        currentCategory = category
        customBookId = -1  // 切换分类时清除词库ID限制
        loadPage(reset = true)
    }

    private fun loadPage(reset: Boolean) {
        if (isLoading) return
        if (!reset && !hasMore) return

        isLoading = true
        binding.progressLoadMore.visibility = android.view.View.VISIBLE

        scope.launch(Dispatchers.IO) {
            try {
                if (reset) {
                    offset = 0
                    loadedWords.clear()
                    totalCount = wordDao.countWords(
                        category = currentCategory,
                        customBookId = customBookId,
                        keyword = currentKeyword.ifBlank { null }
                    )
                }

                val page = wordDao.getWordsPaged(
                    category = currentCategory,
                    customBookId = customBookId,
                    keyword = currentKeyword.ifBlank { null },
                    limit = PAGE_SIZE,
                    offset = offset
                )

                withContext(Dispatchers.Main) {
                    if (reset) {
                        loadedWords.clear()
                    }
                    loadedWords.addAll(page)
                    offset = loadedWords.size
                    hasMore = loadedWords.size < totalCount

                    adapter.submitList(loadedWords.toList())
                    val prefix = if (currentKeyword.isBlank()) "已加载" else "搜索结果"
                    binding.tvWordCount.text = "$prefix：${loadedWords.size} / $totalCount"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@WordLibraryActivity,
                        "加载失败：${e.message ?: "未知错误"}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    binding.progressLoadMore.visibility = android.view.View.GONE
                }
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
        searchJob?.cancel()
        scope.cancel()
    }
}
