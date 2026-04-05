package com.example.myapplication.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.ai.ModelManager
import com.example.myapplication.algorithm.EbbinghausAlgorithm
import com.example.myapplication.database.CustomWordBookDao
import com.example.myapplication.database.ErrorWordDao
import com.example.myapplication.database.StudyRecordDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityDictationBinding
import com.example.myapplication.model.Word
import com.example.myapplication.tts.TTSManager
import kotlinx.coroutines.*

class DictationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_CUSTOM_BOOK_ID = "extra_custom_book_id"
        const val MODE_EN_TO_CN = 0
        const val MODE_CN_TO_EN = 1
    }

    private lateinit var binding: ActivityDictationBinding
    private val wordDao by lazy { WordDao(this) }
    private val customBookDao by lazy { CustomWordBookDao(this) }
    private val studyRecordDao by lazy { StudyRecordDao(this) }
    private val errorWordDao by lazy { ErrorWordDao(this) }
    private val modelManager by lazy { ModelManager.getInstance(this) }
    private var ttsManager: TTSManager? = null

    private var mode = MODE_EN_TO_CN
    private var category = "四级"
    private var customBookId = 0
    private var wordList = mutableListOf<Word>()
    private var currentIndex = 0
    private var isJudging = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getIntExtra(EXTRA_MODE, MODE_EN_TO_CN)
        category = intent.getStringExtra(EXTRA_CATEGORY) ?: "四级"
        customBookId = intent.getIntExtra(EXTRA_CUSTOM_BOOK_ID, 0)

        ttsManager = TTSManager(this)
        loadWords()
        setupUI()
    }

    private fun loadWords() {
        scope.launch(Dispatchers.IO) {
            val words = if (customBookId > 0) {
                wordDao.getCustomWordsByBookId(customBookId)
            } else {
                wordDao.getAllWordsByCategory(category)
            }
            withContext(Dispatchers.Main) {
                if (words.isEmpty()) {
                    Toast.makeText(this@DictationActivity, "「$category」词库为空，请先导入单词", Toast.LENGTH_LONG).show()
                    finish()
                    return@withContext
                }
                wordList = words.shuffled().toMutableList()
                currentIndex = 0
                showCurrentWord()
            }
        }
    }

    private fun setupUI() {
        // 模式切换
        binding.btnModeEnToCn.setOnClickListener {
            mode = MODE_EN_TO_CN
            updateModeButtons()
            showCurrentWord()
        }
        binding.btnModeCnToEn.setOnClickListener {
            mode = MODE_CN_TO_EN
            updateModeButtons()
            showCurrentWord()
        }

        // 词库选择
        binding.btnSelectCategory.setOnClickListener { showCategoryDialog() }

        // 提交判题
        binding.btnSubmit.setOnClickListener { submitAnswer() }

        // 发音
        binding.btnSpeak.setOnClickListener {
            val word = wordList.getOrNull(currentIndex) ?: return@setOnClickListener
            if (mode == MODE_EN_TO_CN) {
                ttsManager?.speak(word.word)
            } else {
                ttsManager?.speak(word.definition)
            }
        }

        // 跳过
        binding.btnSkip.setOnClickListener { nextWord() }

        updateModeButtons()
    }

    private fun updateModeButtons() {
        if (mode == MODE_EN_TO_CN) {
            binding.btnModeEnToCn.isSelected = true
            binding.btnModeCnToEn.isSelected = false
            binding.etAnswer.hint = "请输入中文释义"
        } else {
            binding.btnModeEnToCn.isSelected = false
            binding.btnModeCnToEn.isSelected = true
            binding.etAnswer.hint = "请输入英文单词"
        }
    }

    private fun showCurrentWord() {
        if (wordList.isEmpty()) return
        val word = wordList[currentIndex]
        binding.etAnswer.text?.clear()
        binding.cardResult.visibility = View.GONE
        binding.btnSubmit.isEnabled = true
        binding.etAnswer.isEnabled = true

        if (mode == MODE_EN_TO_CN) {
            binding.tvMainContent.text = word.word
            binding.tvSubContent.text = word.phonetic ?: ""
        } else {
            binding.tvMainContent.text = word.definition
            binding.tvSubContent.text = word.example ?: ""
        }

        binding.tvProgress.text = "${currentIndex + 1} / ${wordList.size}"
        binding.tvCategory.text = category
    }

    private fun submitAnswer() {
        if (isJudging) return
        val word = wordList.getOrNull(currentIndex) ?: return
        var userInput = binding.etAnswer.text.toString().trim()

        // 移除中文标点符号
        userInput = userInput
            .replace("，", "")
            .replace("。", "")
            .replace("！", "")
            .replace("？", "")
            .replace("、", "")
            .replace("；", "")
            .replace("：", "")
            .replace("'", "")
            .replace("'", "")
            .replace(""", "")
            .replace(""", "")
            .replace("…", "")
            .replace("—", "")
            .replace(Regex("\\s+"), " ")

        if (userInput.isBlank()) {
            Toast.makeText(this, "请先输入答案", Toast.LENGTH_SHORT).show()
            return
        }

        // ⚡ 快速通道：精确匹配，无需调用 AI
        val quickResult = quickJudge(word.word, word.definition, userInput)
        if (quickResult != null) {
            showResult(word, quickResult, word.definition, "")
            saveStudyRecord(word, quickResult, userInput)
            return
        }

        if (!modelManager.modelReady) {
            Toast.makeText(this, "AI 模型尚未就绪，请稍等", Toast.LENGTH_SHORT).show()
            return
        }

        isJudging = true
        binding.btnSubmit.isEnabled = false
        binding.etAnswer.isEnabled = false
        binding.progressJudging.visibility = View.VISIBLE

        val timeoutRunnable = Runnable {
            if (isJudging) {
                Toast.makeText(this, "判题中，请稍候...", Toast.LENGTH_SHORT).show()
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 1000)

        scope.launch(Dispatchers.IO) {
            val result = modelManager.judge(word.word, word.definition, userInput, mode)
            mainHandler.removeCallbacks(timeoutRunnable)

            withContext(Dispatchers.Main) {
                isJudging = false
                binding.progressJudging.visibility = View.GONE
                showResult(word, result.result, result.definition, result.hint)
                saveStudyRecord(word, result.result, userInput)
            }
        }
    }

    /**
     * 不调 AI 的快速判断：精确匹配立即返回，模糊情况返回 null 交给 AI
     */
    private fun quickJudge(word: String, definition: String, userInput: String): String? {
        return if (mode == MODE_EN_TO_CN) {
            // 英→中：用户输入与标准释义精确匹配（忽略大小写和空格）
            val normInput = userInput.trim().lowercase()
            val normDef = definition.trim().lowercase()
            when {
                normInput == normDef -> "正确"
                // 标准释义包含分号分隔多义时，任一完整义项匹配即可
                normDef.split(Regex("[；;]")).map { it.trim() }.any { it == normInput } -> "正确"
                else -> null  // 交给 AI 判断语义相似度
            }
        } else {
            // 中→英：用户输入与标准单词精确匹配（忽略大小写）
            val normInput = userInput.trim().lowercase()
            val normWord = word.trim().lowercase()
            when {
                normInput == normWord -> "正确"
                // 编辑距离为 1（单个字符拼写错误）→ 部分正确
                editDistance(normInput, normWord) == 1 -> "部分正确"
                else -> null  // 交给 AI 判断
            }
        }
    }

    /** 计算两个字符串的编辑距离 */
    private fun editDistance(a: String, b: String): Int {
        val m = a.length; val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) { 0 } }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) for (j in 1..n) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                       else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[m][n]
    }

    private fun showResult(word: Word, result: String, definition: String, hint: String) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvResultLabel.text = result

        val color = when (result) {
            "正确" -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            "部分正确" -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            else -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
        }
        binding.tvResultLabel.setTextColor(color)
        binding.cardResult.setCardBackgroundColor(color and 0x30FFFFFF or 0x10000000)

        binding.tvResultDefinition.text = "标准释义：$definition"
        if (hint.isNotBlank() && result != "正确") {
            binding.tvResultHint.visibility = View.VISIBLE
            binding.tvResultHint.text = "补充提示：$hint"
        } else {
            binding.tvResultHint.visibility = View.GONE
        }

        if (result == "错误") {
            binding.btnRetry.visibility = View.VISIBLE
            binding.btnRetry.setOnClickListener {
                binding.cardResult.visibility = View.GONE
                binding.etAnswer.text?.clear()
                binding.etAnswer.isEnabled = true
                binding.btnSubmit.isEnabled = true
                binding.btnRetry.visibility = View.GONE
            }
        } else {
            binding.btnRetry.visibility = View.GONE
            // 3秒后自动下一题
            mainHandler.postDelayed({ nextWord() }, 3000)
        }
    }

    private fun saveStudyRecord(word: Word, judgeResult: String, userInput: String) {
        scope.launch(Dispatchers.IO) {
            val existing = studyRecordDao.getRecordByWordId(word.id)
            val currentStatus = existing?.status ?: EbbinghausAlgorithm.STATUS_UNKNOWN
            val reviewCount = existing?.reviewCount ?: 0

            val newStatus = EbbinghausAlgorithm.judgeResultToStatus(judgeResult, currentStatus)
            val nextReviewTime = EbbinghausAlgorithm.calculateNextReviewTime(newStatus, reviewCount)

            studyRecordDao.insertStudyRecord(word.id, newStatus, nextReviewTime)

            if (judgeResult == "错误") {
                errorWordDao.insertErrorWord(word.id, userInput)
            }
        }
    }

    private fun nextWord() {
        binding.cardResult.visibility = View.GONE
        if (currentIndex < wordList.size - 1) {
            currentIndex++
            showCurrentWord()
        } else {
            showFinishDialog()
        }
    }

    private fun showFinishDialog() {
        AlertDialog.Builder(this)
            .setTitle("本轮默写完成！")
            .setMessage("共默写 ${wordList.size} 个单词\n继续加油！")
            .setPositiveButton("再来一轮") { _, _ ->
                wordList.shuffle()
                currentIndex = 0
                showCurrentWord()
            }
            .setNegativeButton("返回首页") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showCategoryDialog() {
        scope.launch(Dispatchers.IO) {
            val categories = wordDao.getAllCategories()
            withContext(Dispatchers.Main) {
                val items = categories.toTypedArray()
                AlertDialog.Builder(this@DictationActivity)
                    .setTitle("选择词库")
                    .setItems(items) { _, which ->
                        val selectedCategory = items[which]
                        category = selectedCategory
                        binding.tvCategory.text = selectedCategory

                        // ✅ 关键修复：查询是否为自定义词库，获取 customBookId
                        scope.launch(Dispatchers.IO) {
                            val customBook = customBookDao.getCustomWordBookByName(selectedCategory)
                            withContext(Dispatchers.Main) {
                                customBookId = customBook?.id ?: 0
                                loadWords()
                            }
                        }
                    }
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        ttsManager?.shutdown()
    }
}
