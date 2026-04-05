package com.example.myapplication.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.ai.ModelManager
import com.example.myapplication.database.CustomWordBookDao
import com.example.myapplication.database.StudyRecordDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val modelManager by lazy { ModelManager.getInstance(this) }
    private val studyRecordDao by lazy { StudyRecordDao(this) }
    private val wordDao by lazy { WordDao(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initModelAsync()
        setupButtons()
        updateStats()
    }

    private fun initModelAsync() {
        binding.tvModelStatus.text = "🚀 模型加载中..."
        binding.progressModel.visibility = View.VISIBLE

        modelManager.initModel(
            onSuccess = {
                runOnUiThread {
                    binding.tvModelStatus.text = "✅ AI 模型已就绪"
                    binding.progressModel.visibility = View.GONE
                }
            },
            onError = { msg ->
                runOnUiThread {
                    binding.tvModelStatus.text = "❌ 模型加载失败"
                    binding.progressModel.visibility = View.GONE
                    Toast.makeText(this, "模型加载失败:\n$msg\n\n请检查模型文件并重启APP", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun setupButtons() {
        binding.btnDictationEnToCn.setOnClickListener {
            startDictation(DictationActivity.MODE_EN_TO_CN)
        }

        binding.btnDictationCnToEn.setOnClickListener {
            startDictation(DictationActivity.MODE_CN_TO_EN)
        }

        binding.btnReview.setOnClickListener {
            startActivity(Intent(this, ReviewActivity::class.java))
        }

        binding.btnWordLibrary.setOnClickListener {
            startActivity(Intent(this, WordLibraryActivity::class.java))
        }

        binding.btnCustomBook.setOnClickListener {
            startActivity(Intent(this, CustomBookManagerActivity::class.java))
        }

        binding.btnErrorWords.setOnClickListener {
            startActivity(Intent(this, ErrorWordsActivity::class.java))
        }

        binding.btnStatistics.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }
    }

    private fun startDictation(mode: Int) {
        scope.launch(Dispatchers.IO) {
            val categories = wordDao.getAllCategories()
            withContext(Dispatchers.Main) {
                if (categories.isEmpty()) {
                    Toast.makeText(this@MainActivity, "暂无词库，请先导入单词", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("选择词库")
                    .setItems(categories.toTypedArray()) { _, which ->
                        val selectedCategory = categories[which]
                        scope.launch(Dispatchers.IO) {
                            val customBook = CustomWordBookDao(this@MainActivity)
                                .getCustomWordBookByName(selectedCategory)
                            withContext(Dispatchers.Main) {
                                val intent = Intent(this@MainActivity, DictationActivity::class.java).apply {
                                    putExtra(DictationActivity.EXTRA_MODE, mode)
                                    putExtra(DictationActivity.EXTRA_CATEGORY, selectedCategory)
                                    putExtra(DictationActivity.EXTRA_CUSTOM_BOOK_ID, customBook?.id ?: 0)
                                }
                                startActivity(intent)
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun updateStats() {
        scope.launch(Dispatchers.IO) {
            val reviewCount = studyRecordDao.getTodayReviewWords().size
            val totalCount = wordDao.getAllWords().size
            withContext(Dispatchers.Main) {
                binding.tvTodayReview.text = "📚 今日待复习：$reviewCount 词"
                binding.tvTotalWords.text = "📖 词库总量：$totalCount 词"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext.cancel()
        modelManager.release()
    }
}

