package com.example.myapplication.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.database.CustomWordBookDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityImportWordBookBinding
import com.example.myapplication.model.CustomWordBook
import kotlinx.coroutines.*
import java.io.BufferedReader

class ImportWordBookActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ImportWordBookActivity"
    }

    private lateinit var binding: ActivityImportWordBookBinding
    private val wordDao by lazy { WordDao(this) }
    private val customBookDao by lazy { CustomWordBookDao(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleCsvFile(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportWordBookBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectFile.setOnClickListener {
            filePickerLauncher.launch("text/csv")
        }

        binding.btnImport.isEnabled = false
    }

    private fun handleCsvFile(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val lines = content.split("\n").filter { it.isNotBlank() }

                // 跳过表头（第一行）
                val wordLines = if (lines.isNotEmpty() && lines[0].contains("word,")) {
                    lines.drop(1)
                } else {
                    lines
                }

                Log.d(TAG, "📖 读取 CSV 文件: ${wordLines.size} 个单词")

                withContext(Dispatchers.Main) {
                    binding.tvFileInfo.text = "✅ 已选择文件: ${wordLines.size} 个单词"
                    binding.btnImport.isEnabled = true
                    binding.btnImport.setOnClickListener {
                        showBookSelectionDialog(wordLines)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ CSV 解析失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImportWordBookActivity, "文件格式错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showBookSelectionDialog(wordLines: List<String>) {
        scope.launch(Dispatchers.IO) {
            val books = customBookDao.getAllCustomWordBooks()
            val bookNames = books.map { it.bookName }.toMutableList()
            bookNames.add(0, "➕ 创建新词库")

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@ImportWordBookActivity)
                    .setTitle("选择目标词库")
                    .setItems(bookNames.toTypedArray()) { _, which ->
                        if (which == 0) {
                            showCreateBookDialog(wordLines)
                        } else {
                            val selectedBook = books[which - 1]
                            importWords(wordLines, selectedBook.id)
                        }
                    }
                    .show()
            }
        }
    }

    private fun showCreateBookDialog(wordLines: List<String>) {
        val editText = android.widget.EditText(this).apply {
            hint = "输入新词库的名称"
        }
        AlertDialog.Builder(this)
            .setTitle("创建新词库")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val bookName = editText.text.toString().trim()
                if (bookName.isBlank()) {
                    Toast.makeText(this, "词库名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                scope.launch(Dispatchers.IO) {
                    val bookId = customBookDao.createCustomWordBook(bookName).toInt()
                    withContext(Dispatchers.Main) {
                        importWords(wordLines, bookId)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importWords(wordLines: List<String>, bookId: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // 查询词库名称，用于设置单词的 word_type
                val bookName = customBookDao.getCustomWordBookById(bookId)?.bookName ?: "自定义"
                Log.d(TAG, "🚀 开始导入 ${wordLines.size} 个单词到词库: $bookName (ID: $bookId)")

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.VISIBLE
                    binding.progressBar.max = wordLines.size
                    binding.progressBar.progress = 0
                }

                var successCount = 0
                wordLines.forEachIndexed { index, line ->
                    try {
                        // CSV 格式: word,phonetic,definition,type
                        val parts = line.split(",").map { it.trim() }
                        if (parts.size >= 3) {
                            val word = parts[0]
                            val phonetic = parts.getOrNull(1) ?: ""
                            val definition = parts.getOrNull(2) ?: ""
                            // ✅ 关键修复：使用词库名作为 word_type，而非 CSV 中的值
                            val wordType = bookName

                            if (word.isNotBlank() && definition.isNotBlank()) {
                                wordDao.addWord(word, phonetic, definition, wordType, isCustom = 1, customBookId = bookId)
                                successCount++
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ 第 ${index + 1} 行解析失败: $line, ${e.message}")
                    }

                    withContext(Dispatchers.Main) {
                        binding.progressBar.progress = index + 1
                        binding.tvProgress.text = "导入进度: ${index + 1}/${wordLines.size}"
                    }
                }

                // 更新词库单词计数
                customBookDao.incrementWordCountByDelta(bookId, successCount)
                Log.d(TAG, "✅ 导入完成: 成功导入 $successCount 个单词到词库 '$bookName'")

                withContext(Dispatchers.Main) {
                    binding.tvProgress.text = "✅ 导入完成: 成功导入 $successCount 个单词"
                    binding.btnImport.isEnabled = false
                    Toast.makeText(this@ImportWordBookActivity, "已导入 $successCount 个单词", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 导入失败: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImportWordBookActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
