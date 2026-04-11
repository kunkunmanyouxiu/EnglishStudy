# EnglishStudy（ONNX 语义判题版）

本项目已将原来的 Gemma 4 LiteRT LM 方案，迁移为 Hugging Face `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` 的 ONNX 句向量方案。

## 模型信息

基于模型页与配置文件，本项目按以下方式接入：

- 模型：`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`
- 任务：多语言语义向量（Sentence Embeddings）
- 向量维度：`384`
- 最大序列长度：`128`
- 池化方式：`mean pooling`

## 必需文件（assets）

请确保以下文件位于 `app/src/main/assets/`：

```text
app/src/main/assets/
├── model.onnx
└── tokenizer.json
```

说明：
- 你已经放入了 `model.onnx`。
- 还需要补充 `tokenizer.json`，否则应用初始化模型时会报错。

## 已完成代码改动

### 1) 依赖迁移

文件：`app/build.gradle.kts`

- 移除：`com.google.ai.edge.litertlm:litertlm-android`
- 新增：`io.gitlab.shubham0204:sentence-embeddings:v6`
- `noCompress` 新增：`onnx`、`json`

### 2) AI 核心重构

文件：`app/src/main/java/com/example/myapplication/ai/ModelManager.kt`

- 模型文件名切换：`gemma-4-E4B-it.litertlm` -> `model.onnx`
- 新增 tokenizer 文件读取：`tokenizer.json`
- 初始化改为 ONNX 句向量引擎
- `judge()` 与 `preJudge()` 改为“向量编码 + 余弦相似度”

## 业务阈值（当前实现）

- 英 -> 中 判题：
  - `>= 0.72`：正确
  - `>= 0.55`：部分正确
  - 其余：错误

- 中 -> 英 判题：
  - `>= 0.78`：正确
  - `>= 0.58`：部分正确
  - 其余：错误

- 预判断（单词 vs 释义）：
  - `>= 0.62`：高
  - `>= 0.45`：中
  - 其余：低

这些阈值可根据你的词库继续微调。

## 构建验证

已执行：

```bash
./gradlew :app:assembleDebug -x test
```

结果：构建成功。

## 常见问题

### 1. 初始化失败，提示找不到 tokenizer.json

请检查路径是否为：

```text
app/src/main/assets/tokenizer.json
```

### 2. 判题结果偏松或偏严

建议结合真实数据微调阈值，并保留现有“快速规则判定”作为第一层过滤。

## 参考链接

- 模型主页：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2>
- sentence_bert_config：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/blob/main/sentence_bert_config.json>
- pooling 配置：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/blob/main/1_Pooling/config.json>
