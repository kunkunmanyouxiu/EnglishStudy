# EnglishStudy（ONNX 版）

本项目已将原来的 Gemma 4 LiteRT LM 判题方案，切换为 Hugging Face `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` 的 ONNX 语义向量方案。

## 1. 模型信息（来自模型页）

- 模型：`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`
- 任务：多语言句向量（Sentence Embedding）
- 典型输出维度：`384`
- 典型最大序列长度：`128`
- 池化方式：`mean pooling`

项目中通过余弦相似度完成：
- 默写判题（正确 / 部分正确 / 错误）
- 自定义词条上传前预判断（高 / 中 / 低）

## 2. 你需要准备的 assets 文件

请把下面两个文件放到 `app/src/main/assets/`：

- `model.onnx`
- `tokenizer.json`

目录示例：

```text
app/src/main/assets/
├── model.onnx
└── tokenizer.json
```

说明：
- 你已经放了 `model.onnx`。
- 还需要从同一模型页面下载 `tokenizer.json`（否则应用会提示模型初始化失败）。

## 3. 代码改动说明

### 3.1 推理框架

- 移除了 `LiteRT LM` 的对话式 Prompt 推理依赖。
- 改为 `Sentence Embeddings Android`（底层 ONNX Runtime + tokenizer）。

文件：`app/build.gradle.kts`

### 3.2 AI 核心逻辑

文件：`app/src/main/java/com/example/myapplication/ai/ModelManager.kt`

主要变化：

- 模型文件名从 `gemma-4-E4B-it.litertlm` 改为 `model.onnx`
- 新增 `tokenizer.json` 读取
- 初始化时创建 `SentenceEmbedding` 实例
- `judge()` / `preJudge()` 改为：
  1. 编码文本为向量
  2. 计算余弦相似度
  3. 按阈值映射到业务结果

## 4. 判题阈值（当前实现）

- 英 -> 中 判题：
  - `>= 0.72`：正确
  - `>= 0.55`：部分正确
  - 其他：错误

- 中 -> 英 判题：
  - `>= 0.78`：正确
  - `>= 0.58`：部分正确
  - 其他：错误

- 预判断（单词 vs 释义）：
  - `>= 0.62`：高
  - `>= 0.45`：中
  - 其他：低

可根据你的词库数据继续微调。

## 5. 编译运行

```bash
./gradlew clean assembleDebug
```

如果首次拉依赖较慢，属正常现象。

## 6. 常见问题

### Q1: 提示找不到 tokenizer.json

请确认路径：

```text
app/src/main/assets/tokenizer.json
```

### Q2: 模型已就绪但判题效果不稳定

建议：
- 优先清理输入中的噪声符号（项目中已做基础清洗）
- 根据实际数据微调阈值
- 适当增加“快速规则判定”覆盖率（例如固定短语、常见缩写）

## 参考链接

- 模型主页：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2>
- sentence_bert_config：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/blob/main/sentence_bert_config.json>
- pooling 配置：<https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/blob/main/1_Pooling/config.json>
