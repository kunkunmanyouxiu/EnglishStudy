package com.example.myapplication.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.example.myapplication.model.JudgeResult
import com.example.myapplication.model.PreJudgeResult
import java.io.File
import java.io.FileOutputStream

class ModelManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_FILENAME = "gemma-4-E4B-it.litertlm"
        private const val BUFFER_SIZE = 16 * 1024 * 1024 // 16MB

        @Volatile
        private var INSTANCE: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var isInitialized = false
    private var isLoading = false

    val modelReady: Boolean get() = isInitialized && engine != null && conversation != null

    /**
     * 初始化模型（异步，仅执行一次）
     */
    fun initModel(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (isInitialized) {
            onSuccess()
            return
        }
        if (isLoading) return
        isLoading = true

        Thread {
            try {
                Log.i(TAG, "🚀 开始初始化 LiteRT LM 模型...")

                // 从 assets 复制模型到内部存储
                val modelFile = copyModelFileFromAssets()

                if (!modelFile.exists()) {
                    throw RuntimeException("模型文件不存在: ${modelFile.absolutePath}")
                }

                val fileSize = modelFile.length()
                Log.i(TAG, "📦 模型文件大小: ${fileSize / (1024L * 1024L)}MB")

                if (fileSize < 100 * 1024 * 1024) {
                    throw RuntimeException("模型文件太小（可能不完整）: ${fileSize}字节")
                }

                Log.i(TAG, "⏳ 正在加载模型引擎...")

                val config = EngineConfig(modelPath = modelFile.absolutePath)
                engine = Engine(config)
                Log.i(TAG, "✅ 模型引擎创建成功")

                Log.i(TAG, "⏳ 初始化模型引擎（这需要一些时间）...")
                engine?.initialize()
                Log.i(TAG, "✅ 模型引擎初始化成功")

                Log.i(TAG, "⏳ 创建对话会话...")
                val conversationConfig = ConversationConfig()
                conversation = engine?.createConversation(conversationConfig)
                Log.i(TAG, "✅ 对话会话创建成功")

                isInitialized = true
                isLoading = false
                Log.i(TAG, "✅✅✅ LiteRT LM 模型加载完成！")
                onSuccess()

            } catch (e: Exception) {
                isLoading = false
                Log.e(TAG, "❌ 模型加载失败: ${e.message}", e)
                onError("模型加载失败: ${e.message ?: e.javaClass.simpleName}")
            }
        }.start()
    }

    /**
     * 从 assets 复制模型文件到内部存储（仅首次安装时执行）
     */
    private fun copyModelFileFromAssets(): File {
        val modelDir = context.getDir("models", Context.MODE_PRIVATE)
        val modelFile = File(modelDir, MODEL_FILENAME)

        if (modelFile.exists() && modelFile.length() > 100 * 1024 * 1024) {
            Log.i(TAG, "✓ 模型文件已存在: ${modelFile.absolutePath}")
            return modelFile
        }

        if (modelFile.exists()) {
            Log.w(TAG, "⚠️ 删除不完整的旧文件...")
            modelFile.delete()
        }

        Log.i(TAG, "⏳ 从 assets 复制模型文件...")
        Log.i(TAG, "这是一个 2.8GB 的大文件，请耐心等待（可能需要 5-10 分钟）")

        val startTime = System.currentTimeMillis()
        var totalBytes = 0L
        val lastLogTime = LongArray(1) { startTime }

        try {
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            context.assets.open(MODEL_FILENAME).use { input ->
                FileOutputStream(modelFile).use { output ->
                    while (input.read(buffer).also { bytesRead = it } > 0) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastLogTime[0] > 5000) {
                            val progress = totalBytes / (1024L * 1024L)
                            val speed = if (now > startTime) (totalBytes / (now - startTime)) else 0
                            Log.i(TAG, "⏳ 已复制: ${progress}MB，速度: ${speed}KB/s")
                            lastLogTime[0] = now
                        }
                    }
                }
            }

            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            Log.i(TAG, "✅ 复制完成: ${totalBytes / (1024L * 1024L)}MB，用时: ${elapsed}秒")
            return modelFile

        } catch (e: Exception) {
            Log.e(TAG, "❌ 复制失败: ${e.message}", e)
            if (modelFile.exists()) modelFile.delete()
            throw e
        }
    }

    /**
     * AI 判题（mode: 0=英→中, 1=中→英）
     */
    fun judge(word: String, definition: String, userInput: String, mode: Int = 0): JudgeResult {
        val conv = conversation ?: return JudgeResult("错误", definition, "模型未加载")

        val prompt = buildJudgePrompt(word, definition, userInput, mode)
        return try {
            Log.d(TAG, "📝 发送判题 Prompt...")
            val response = conv.sendMessage(prompt)
            val responseText = response.toString().trim()
            Log.d(TAG, "📤 模型返回: $responseText")
            parseJudgeResult(responseText, definition)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 判题失败: ${e.message}", e)
            JudgeResult("错误", definition, "判题异常：${e.message}")
        }
    }

    /**
     * AI 预判断
     */
    fun preJudge(customWord: String, customDefinition: String): PreJudgeResult {
        val conv = conversation
            ?: return PreJudgeResult("低", "模型未加载", "请等待模型初始化完成")

        val prompt = buildPreJudgePrompt(customWord, customDefinition)
        return try {
            Log.d(TAG, "📝 发送预判断 Prompt...")
            val response = conv.sendMessage(prompt)
            val responseText = response.toString()
            Log.d(TAG, "📤 模型返回: $responseText")
            parsePreJudgeResult(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 预判断失败: ${e.message}", e)
            PreJudgeResult("低", "预判断异常", e.message ?: "未知错误")
        }
    }

    private fun buildJudgePrompt(word: String, definition: String, userInput: String, mode: Int): String {
        return if (mode == 0) {
            // 英→中：判断语义是否一致，只输出结论
            "英文单词「$word」标准释义：$definition\n用户回答：$userInput\n语义是否一致？只回复：正确、部分正确、错误"
        } else {
            // 中→英：判断拼写和语义，只输出结论
            "中文「$definition」对应英文单词是「$word」\n用户回答：$userInput\n是否正确？只回复：正确、部分正确、错误"
        }
    }

    private fun buildPreJudgePrompt(customWord: String, customDefinition: String): String {
        return """你是一个英语单词语义匹配审核员。判断用户上传的英文单词与中文释义是否语义匹配。

用户上传单词：$customWord
用户上传释义：$customDefinition

匹配度等级：高/中/低
高：单词与释义完全匹配，释义准确覆盖核心含义
中：基本匹配，但表述不精准或遗漏次要含义
低：完全不匹配，语义无关或相反

输出格式（必须严格遵守）：
匹配度等级：高/中/低
匹配说明：（不超过50字）
优化建议：（仅匹配度为中/低时填写，不超过30字）"""
    }

    private fun parseJudgeResult(response: String, definition: String): JudgeResult {
        val lines = response.trim().split("\n")
        var result = "错误"
        var hint = ""

        for (line in lines) {
            when {
                line.contains("结果") -> {
                    result = when {
                        line.contains("正确") && !line.contains("部分") -> "正确"
                        line.contains("部分正确") -> "部分正确"
                        else -> "错误"
                    }
                }
                line.contains("补充提示") || line.contains("提示") -> {
                    hint = line.substringAfter("：").substringAfter(":").trim()
                }
            }
        }

        return JudgeResult(result, definition, hint)
    }

    private fun parsePreJudgeResult(response: String): PreJudgeResult {
        val lines = response.trim().split("\n")
        var matchLevel = "低"
        var matchExplanation = ""
        var optimizationSuggestion = ""

        for (line in lines) {
            when {
                line.contains("匹配度等级") -> {
                    matchLevel = when {
                        line.contains("高") -> "高"
                        line.contains("中") -> "中"
                        else -> "低"
                    }
                }
                line.contains("匹配说明") -> {
                    matchExplanation = line.substringAfter("：").substringAfter(":").trim()
                }
                line.contains("优化建议") -> {
                    optimizationSuggestion = line.substringAfter("：").substringAfter(":").trim()
                }
            }
        }

        return PreJudgeResult(matchLevel, matchExplanation, optimizationSuggestion)
    }

    fun release() {
        try {
            conversation = null
            engine = null
        } catch (e: Exception) {
            Log.w(TAG, "关闭模型时出错: ${e.message}")
        }
        isInitialized = false
        isLoading = false
        synchronized(ModelManager::class.java) {
            if (INSTANCE === this) INSTANCE = null
        }
    }
}
