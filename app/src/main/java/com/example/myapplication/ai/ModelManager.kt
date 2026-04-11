package com.example.myapplication.ai

import android.content.Context
import android.util.Log
import com.example.myapplication.model.JudgeResult
import com.example.myapplication.model.PreJudgeResult
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

class ModelManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val MODEL_FILENAME = "model.onnx"
        private const val TOKENIZER_FILENAME = "tokenizer.json"
        private const val OUTPUT_TENSOR_NAME = "last_hidden_state"
        private const val BUFFER_SIZE = 4 * 1024 * 1024
        private const val MIN_MODEL_FILE_BYTES = 10L * 1024L * 1024L

        @Volatile
        private var INSTANCE: ModelManager? = null

        fun getInstance(context: Context): ModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var sentenceEmbedding: SentenceEmbedding? = null
    private var isInitialized = false
    private var isLoading = false

    val modelReady: Boolean
        get() = isInitialized && sentenceEmbedding != null

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
                Log.i(TAG, "开始初始化 ONNX 语义模型...")

                val modelFile = copyAssetToInternalFile(MODEL_FILENAME)
                val tokenizerBytes = readTokenizerBytes()

                if (!modelFile.exists() || modelFile.length() < MIN_MODEL_FILE_BYTES) {
                    throw RuntimeException("模型文件异常: ${modelFile.absolutePath}")
                }

                val embedding = SentenceEmbedding()
                runBlocking {
                    embedding.init(
                        modelFile.absolutePath,
                        tokenizerBytes,
                        true,
                        OUTPUT_TENSOR_NAME,
                        false,
                        true,
                        true
                    )
                }

                sentenceEmbedding = embedding
                isInitialized = true
                Log.i(TAG, "ONNX 语义模型加载完成")
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "模型加载失败: ${e.message}", e)
                onError("模型加载失败: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                isLoading = false
            }
        }.start()
    }

    /**
     * AI 判题（mode: 0=英→中, 1=中→英）
     */
    fun judge(word: String, definition: String, userInput: String, mode: Int): JudgeResult {
        val model = sentenceEmbedding ?: return JudgeResult("错误", definition, "模型未加载")

        return try {
            val (referenceText, candidateText) = if (mode == 0) {
                definition to userInput
            } else {
                word to userInput
            }

            val referenceEmbedding = encodeText(model, referenceText)
            val candidateEmbedding = encodeText(model, candidateText)
            val similarity = cosineSimilarity(referenceEmbedding, candidateEmbedding)

            val result = if (mode == 0) {
                when {
                    similarity >= 0.72f -> "正确"
                    similarity >= 0.55f -> "部分正确"
                    else -> "错误"
                }
            } else {
                when {
                    similarity >= 0.78f -> "正确"
                    similarity >= 0.58f -> "部分正确"
                    else -> "错误"
                }
            }

            val hint = when (result) {
                "正确" -> ""
                "部分正确" -> "语义接近但不够精准（相似度 ${formatScore(similarity)}）"
                else -> "与标准答案语义差异较大（相似度 ${formatScore(similarity)}）"
            }

            JudgeResult(result, definition, hint)
        } catch (e: Exception) {
            Log.e(TAG, "判题失败: ${e.message}", e)
            JudgeResult("错误", definition, "判题异常：${e.message ?: "未知错误"}")
        }
    }

    /**
     * AI 预判断（单词与释义匹配度）
     */
    fun preJudge(customWord: String, customDefinition: String): PreJudgeResult {
        val model = sentenceEmbedding ?: return PreJudgeResult("低", "模型未加载", "请等待模型初始化完成")

        return try {
            val wordEmbedding = encodeText(model, customWord)
            val definitionEmbedding = encodeText(model, customDefinition)
            val similarity = cosineSimilarity(wordEmbedding, definitionEmbedding)

            val matchLevel = when {
                similarity >= 0.80f -> "高"
                similarity >= 0.45f -> "中"
                else -> "低"
            }

            val explanation = when (matchLevel) {
                "高" -> "语义匹配度高（相似度 ${formatScore(similarity)}）"
                "中" -> "语义基本相关，但表达可能不够精确（相似度 ${formatScore(similarity)}）"
                else -> "语义相关性较低（相似度 ${formatScore(similarity)}）"
            }

            val suggestion = when (matchLevel) {
                "高" -> ""
                "中" -> "建议补充更具体的释义或词性"
                else -> "建议核对单词拼写并重写释义"
            }

            PreJudgeResult(matchLevel, explanation, suggestion)
        } catch (e: Exception) {
            Log.e(TAG, "预判断失败: ${e.message}", e)
            PreJudgeResult("低", "预判断异常", e.message ?: "未知错误")
        }
    }

    private fun copyAssetToInternalFile(assetName: String): File {
        val modelDir = context.getDir("models", Context.MODE_PRIVATE)
        val targetFile = File(modelDir, assetName)

        if (targetFile.exists() && targetFile.length() > MIN_MODEL_FILE_BYTES) {
            return targetFile
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }

        val buffer = ByteArray(BUFFER_SIZE)
        context.assets.open(assetName).use { input ->
            FileOutputStream(targetFile).use { output ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } > 0) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }

        return targetFile
    }

    private fun readTokenizerBytes(): ByteArray {
        return try {
            context.assets.open(TOKENIZER_FILENAME).use { it.readBytes() }
        } catch (e: Exception) {
            throw RuntimeException(
                "未找到 $TOKENIZER_FILENAME，请从 Hugging Face 下载该文件并放入 app/src/main/assets/"
            )
        }
    }

    private fun encodeText(model: SentenceEmbedding, text: String): FloatArray {
        return runBlocking {
            model.encode(text)
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val size = minOf(a.size, b.size)

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in 0 until size) {
            val av = a[i].toDouble()
            val bv = b[i].toDouble()
            dot += av * bv
            normA += av * av
            normB += bv * bv
        }

        if (normA <= 0.0 || normB <= 0.0) return 0f
        return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).toFloat()
    }

    private fun formatScore(score: Float): String = "%.2f".format(score)

    fun release() {
        sentenceEmbedding?.let { embedding ->
            // 兼容不同版本库的资源释放方法
            for (methodName in arrayOf("close", "release", "destroy")) {
                try {
                    val method = embedding.javaClass.methods.firstOrNull {
                        it.name == methodName && it.parameterCount == 0
                    }
                    method?.invoke(embedding)
                } catch (_: Exception) {
                    // ignore
                }
            }
        }

        sentenceEmbedding = null
        isInitialized = false
        isLoading = false

        synchronized(ModelManager::class.java) {
            if (INSTANCE === this) INSTANCE = null
        }
    }
}
