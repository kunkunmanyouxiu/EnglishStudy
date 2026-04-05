package com.example.myapplication.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.ErrorWordAdapter
import com.example.myapplication.database.ErrorWordDao
import com.example.myapplication.databinding.ActivityErrorWordsBinding
import kotlinx.coroutines.*

class ErrorWordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityErrorWordsBinding
    private val errorWordDao by lazy { ErrorWordDao(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var adapter: ErrorWordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityErrorWordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 在 onCreate 中初始化 adapter
        adapter = ErrorWordAdapter { errorWord ->
            AlertDialog.Builder(this)
                .setTitle("从错题本移除")
                .setMessage("确定从错题本移除「${errorWord.word}」吗？")
                .setPositiveButton("确定") { _, _ ->
                    scope.launch(Dispatchers.IO) {
                        errorWordDao.deleteErrorWord(errorWord.wordId)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ErrorWordsActivity, "已移除", Toast.LENGTH_SHORT).show()
                            loadErrorWords()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.rvErrorWords.layoutManager = LinearLayoutManager(this)
        binding.rvErrorWords.adapter = adapter

        binding.btnPracticeErrors.setOnClickListener {
            val intent = android.content.Intent(this, DictationActivity::class.java).apply {
                putExtra(DictationActivity.EXTRA_CATEGORY, "错题本")
            }
            startActivity(intent)
        }

        loadErrorWords()
    }

    private fun loadErrorWords() {
        scope.launch(Dispatchers.IO) {
            val errors = errorWordDao.getAllErrorWords()
            withContext(Dispatchers.Main) {
                // 确保传入列表而不是 null
                val errorList = errors ?: emptyList()
                adapter.submitList(errorList)
                binding.tvErrorCount.text = "共 ${errorList.size} 个错题"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
