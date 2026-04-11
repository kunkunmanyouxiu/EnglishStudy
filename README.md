# EnglishStudy（当前主线：ONNX 语义判题）

一个纯本地运行的英语单词默写应用。

当前版本已从旧的 Gemma LiteRT 方案迁移到 Hugging Face 句向量模型：

- 模型：`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`
- 推理方式：ONNX Runtime + tokenizer（Android 端）
- 核心能力：语义判题 + 上传前语义匹配预判断

## 1. 当前技术栈

- Android: `minSdk 26`, `targetSdk 36`, `compileSdk 36`
- Kotlin JVM Target: `11`
- 本地数据库: SQLite
- AI 推理依赖: `io.gitlab.shubham0204:sentence-embeddings:v6`

关键配置文件：

- `app/build.gradle.kts`
- `app/src/main/java/com/example/myapplication/ai/ModelManager.kt`

## 2. 模型文件准备（必须）

请将以下文件放入 `app/src/main/assets/`：

```text
app/src/main/assets/
├── model.onnx
└── tokenizer.json
```

说明：

- `model.onnx`：你已放入（当前约 470MB）
- `tokenizer.json`：必须存在（当前约 9MB）
- 若缺失 `tokenizer.json`，应用初始化模型会失败

## 3. 快速启动

1. 打开项目（Android Studio）
2. 同步 Gradle
3. 构建：

```bash
./gradlew :app:assembleDebug -x test
```

4. 运行到设备/模拟器

## 4. AI 判题逻辑（当前实现）

### 4.1 双层判定

- 第一层：规则快速判定（无需 AI）
  - 英 -> 中：完全匹配（含分号分义匹配）直接判“正确”
  - 中 -> 英：完全匹配判“正确”，编辑距离为 1 判“部分正确”
- 第二层：ONNX 语义相似度判定
  - 规则无法确定时，调用句向量模型计算余弦相似度

### 4.2 阈值

`ModelManager.kt` 当前阈值如下：

- 英 -> 中：
  - `>= 0.72` => 正确
  - `>= 0.55` => 部分正确
  - 其余 => 错误

- 中 -> 英：
  - `>= 0.78` => 正确
  - `>= 0.58` => 部分正确
  - 其余 => 错误

- 上传前预判断（单词 vs 释义）：
  - `>= 0.80` => 高
  - `>= 0.45` => 中
  - 其余 => 低

## 5. 关键目录

```text
app/src/main/
├── assets/
│   ├── model.onnx
│   └── tokenizer.json
└── java/com/example/myapplication/
    ├── ai/ModelManager.kt
    ├── ui/DictationActivity.kt
    ├── ui/CustomWordUploadActivity.kt
    ├── database/
    └── ...
```

## 6. 常见问题

### Q1. Android Studio 里 `import com.ml.shubham0204...` 报红

通常是 IDE 同步/索引缓存问题，不是代码包名错误。

排查顺序：

1. `File -> Sync Project with Gradle Files`
2. 关闭 `Gradle Offline Work`（如果开启）
3. `Build -> Rebuild Project`
4. 仍不行：`File -> Invalidate Caches / Restart`

### Q2. 模型初始化失败，提示找不到 tokenizer

确认文件存在：

```text
app/src/main/assets/tokenizer.json
```

### Q3. 判题偏松或偏严

请基于你的真实词库微调阈值（`ModelManager.kt`），建议每次微调后做小样本回归验证。

## 7. 参考链接

- 模型主页：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2>
- sentence_bert_config：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/blob/main/sentence_bert_config.json>
- pooling 配置：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/blob/main/1_Pooling/config.json>
- Android 句向量库：<https://github.com/shubham0204/Sentence-Embeddings-Android>
