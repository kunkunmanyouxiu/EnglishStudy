package com.example.myapplication.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.ai.ModelManager
import com.example.myapplication.database.CustomWordBookDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityCustomWordUploadBinding
import com.example.myapplication.model.Word
import kotlinx.coroutines.*

class CustomWordUploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomWordUploadBinding
    private val wordDao by lazy { WordDao(this) }
    private val customBookDao by lazy { CustomWordBookDao(this) }
    private val modelManager by lazy { ModelManager.getInstance(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var selectedBookId = 0
    private var lastMatchLevel = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomWordUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
    }

    private fun setupButtons() {
        binding.btnSelectBook.setOnClickListener { selectBook() }

        binding.btnPreJudge.setOnClickListener { doPreJudge() }

        binding.btnUpload.setOnClickListener { uploadWord() }

        binding.btnForceUpload.setOnClickListener { forceUpload() }
    }

    private fun selectBook() {
        scope.launch(Dispatchers.IO) {
            val books = customBookDao.getAllCustomWordBooks()
            withContext(Dispatchers.Main) {
                if (books.isEmpty()) {
                    AlertDialog.Builder(this@CustomWordUploadActivity)
                        .setTitle("没有自定义词库")
                        .setMessage("请先创建一个自定义词库")
                        .setPositiveButton("去创建") { _, _ ->
                            startActivity(android.content.Intent(this@CustomWordUploadActivity, CustomBookManagerActivity::class.java))
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    return@withContext
                }
                val items = books.map { it.bookName }.toTypedArray()
                val ids = books.map { it.id }
                AlertDialog.Builder(this@CustomWordUploadActivity)
                    .setTitle("选择词库")
                    .setItems(items) { _, which ->
                        selectedBookId = ids[which]
                        binding.tvSelectedBook.text = "当前词库：${items[which]}"
                    }
                    .show()
            }
        }
    }

    private fun doPreJudge() {
        val word = binding.etWord.text.toString().trim()
        val definition = binding.etDefinition.text.toString().trim()

        if (word.isBlank() || definition.isBlank()) {
            Toast.makeText(this, "请填写单词和释义", Toast.LENGTH_SHORT).show()
            return
        }
        if (!modelManager.modelReady) {
            Toast.makeText(this, "AI 模型尚未就绪，请稍等", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnPreJudge.isEnabled = false
        binding.progressPreJudge.visibility = View.VISIBLE
        binding.cardPreJudgeResult.visibility = View.GONE
        binding.btnUpload.visibility = View.GONE
        binding.btnForceUpload.visibility = View.GONE

        val timeoutToast = Runnable {
            Toast.makeText(this, "预判断中，请稍候...", Toast.LENGTH_SHORT).show()
        }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed(timeoutToast, 1000)

        scope.launch(Dispatchers.IO) {
            val result = modelManager.preJudge(word, definition)
            handler.removeCallbacks(timeoutToast)

            withContext(Dispatchers.Main) {
                binding.btnPreJudge.isEnabled = true
                binding.progressPreJudge.visibility = View.GONE
                lastMatchLevel = result.matchLevel

                binding.cardPreJudgeResult.visibility = View.VISIBLE
                binding.tvMatchLevel.text = "匹配度：${result.matchLevel}"
                binding.tvMatchExplanation.text = result.matchExplanation

                val levelColor = when (result.matchLevel) {
                    "高" -> ContextCompat.getColor(this@CustomWordUploadActivity, android.R.color.holo_green_dark)
                    "中" -> ContextCompat.getColor(this@CustomWordUploadActivity, android.R.color.holo_orange_dark)
                    else -> ContextCompat.getColor(this@CustomWordUploadActivity, android.R.color.holo_red_dark)
                }
                binding.tvMatchLevel.setTextColor(levelColor)

                when (result.matchLevel) {
                    "高" -> {
                        binding.tvPreJudgeMessage.text = "匹配度高，可直接上传"
                        binding.tvOptSuggestion.visibility = View.GONE
                        binding.btnUpload.visibility = View.VISIBLE
                        binding.btnForceUpload.visibility = View.GONE
                    }
                    "中" -> {
                        binding.tvPreJudgeMessage.text = "匹配度中等，建议优化释义"
                        binding.tvOptSuggestion.visibility = View.VISIBLE
                        binding.tvOptSuggestion.text = "优化建议：${result.optimizationSuggestion}"
                        binding.btnUpload.visibility = View.VISIBLE
                        binding.btnForceUpload.visibility = View.VISIBLE
                        binding.btnForceUpload.text = "忽略建议，上传"
                    }
                    else -> {
                        binding.tvPreJudgeMessage.text = "匹配度低，单词与释义不匹配，请修改后重试"
                        binding.tvOptSuggestion.visibility = View.VISIBLE
                        binding.tvOptSuggestion.text = "优化建议：${result.optimizationSuggestion}"
                        binding.btnUpload.visibility = View.GONE
                        binding.btnForceUpload.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun uploadWord() {
        val word = binding.etWord.text.toString().trim()
        val definition = binding.etDefinition.text.toString().trim()
        val phonetic = binding.etPhonetic.text.toString().trim().ifBlank { null }
        val example = binding.etExample.text.toString().trim().ifBlank { null }

        if (selectedBookId == 0) {
            Toast.makeText(this, "请先选择词库", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch(Dispatchers.IO) {
            val newWord = Word(
                word = word,
                phonetic = phonetic,
                definition = definition,
                example = example,
                wordType = "自定义",
                isCustom = true,
                customBookId = selectedBookId
            )
            wordDao.insertWord(newWord)
            customBookDao.incrementWordCount(selectedBookId)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@CustomWordUploadActivity, "上传成功！", Toast.LENGTH_SHORT).show()
                clearForm()
            }
        }
    }

    private fun forceUpload() {
        AlertDialog.Builder(this)
            .setTitle("强制上传")
            .setMessage("该单词与释义匹配度较低，确定要上传吗？")
            .setPositiveButton("确定上传") { _, _ -> uploadWord() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearForm() {
        binding.etWord.text?.clear()
        binding.etPhonetic.text?.clear()
        binding.etDefinition.text?.clear()
        binding.etExample.text?.clear()
        binding.cardPreJudgeResult.visibility = View.GONE
        binding.btnUpload.visibility = View.GONE
        binding.btnForceUpload.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
