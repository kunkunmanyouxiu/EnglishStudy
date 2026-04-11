package com.example.myapplication.ui

import android.content.Intent
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
import java.time.LocalDate

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

        binding.btnLearnWords.setOnClickListener {
            startLearnWords()
        }

        binding.btnLearnedWords.setOnClickListener {
            startActivity(Intent(this, LearnedWordsActivity::class.java))
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
                                showDictationConfigDialog(
                                    mode = mode,
                                    category = selectedCategory,
                                    customBookId = customBook?.id ?: 0
                                )
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun showDictationConfigDialog(mode: Int, category: String, customBookId: Int) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        val etCount = EditText(this).apply {
            hint = "默写个数（留空表示全部）"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val cbUseLearnedDate = CheckBox(this).apply {
            text = "仅默写某天已学习单词"
        }
        val tvDate = TextView(this).apply {
            val today = LocalDate.now().toString()
            text = "学习日期：$today"
            setPadding(0, 8, 0, 0)
        }

        container.addView(etCount)
        container.addView(cbUseLearnedDate)
        container.addView(tvDate)

        var selectedDate = LocalDate.now()
        tvDate.visibility = View.GONE

        cbUseLearnedDate.setOnCheckedChangeListener { _, isChecked ->
            tvDate.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                DatePickerDialog(
                    this,
                    { _, year, month, dayOfMonth ->
                        selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                        tvDate.text = "学习日期：$selectedDate"
                    },
                    selectedDate.year,
                    selectedDate.monthValue - 1,
                    selectedDate.dayOfMonth
                ).show()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("默写配置")
            .setMessage("词库：$category")
            .setView(container)
            .setPositiveButton("开始默写") { _, _ ->
                val wordLimit = etCount.text.toString().trim().toIntOrNull()?.coerceAtLeast(1) ?: 0
                val learnedDate = if (cbUseLearnedDate.isChecked) selectedDate.toString() else ""

                val intent = Intent(this, DictationActivity::class.java).apply {
                    putExtra(DictationActivity.EXTRA_MODE, mode)
                    putExtra(DictationActivity.EXTRA_CATEGORY, category)
                    putExtra(DictationActivity.EXTRA_CUSTOM_BOOK_ID, customBookId)
                    putExtra(DictationActivity.EXTRA_WORD_LIMIT, wordLimit)
                    putExtra(DictationActivity.EXTRA_LEARNED_DATE, learnedDate)
                }
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startLearnWords() {
        scope.launch(Dispatchers.IO) {
            val categories = wordDao.getAllCategories()
            withContext(Dispatchers.Main) {
                if (categories.isEmpty()) {
                    Toast.makeText(this@MainActivity, "暂无词库，请先导入单词", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                AlertDialog.Builder(this@MainActivity)
                    .setTitle("选择学习词库")
                    .setItems(categories.toTypedArray()) { _, which ->
                        val selectedCategory = categories[which]
                        scope.launch(Dispatchers.IO) {
                            val customBook = CustomWordBookDao(this@MainActivity)
                                .getCustomWordBookByName(selectedCategory)
                            withContext(Dispatchers.Main) {
                                showLearnConfigDialog(
                                    category = selectedCategory,
                                    customBookId = customBook?.id ?: 0
                                )
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun showLearnConfigDialog(category: String, customBookId: Int) {
        val etCount = EditText(this).apply {
            hint = "学习个数（留空表示全部）"
            inputType = InputType.TYPE_CLASS_NUMBER
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }

        AlertDialog.Builder(this)
            .setTitle("学习配置")
            .setMessage("词库：$category\n学习日期：${LocalDate.now()}")
            .setView(etCount)
            .setPositiveButton("开始学习") { _, _ ->
                val learnCount = etCount.text.toString().trim().toIntOrNull()?.coerceAtLeast(1) ?: 0
                val intent = Intent(this, LearnWordsActivity::class.java).apply {
                    putExtra(LearnWordsActivity.EXTRA_CATEGORY, category)
                    putExtra(LearnWordsActivity.EXTRA_CUSTOM_BOOK_ID, customBookId)
                    putExtra(LearnWordsActivity.EXTRA_LEARN_COUNT, learnCount)
                }
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
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
