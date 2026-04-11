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

## 更新说明（2026-04-11）

本次在原有功能基础上新增了“学习单词”与“默写范围配置”能力：

### 1) 新增学习单词流程

- 可先选择学习词库，再自定义学习个数（留空为全部）。
- 学习单词按随机顺序展示，并按“每页 10 个单词”分页。
- 学习页面为左右对照：左侧单词，右侧释义。
- 支持遮挡左侧或右侧（便于自测）。
- 点击“下一页并标记已学”后，会把当前页单词写入已学习记录，并标注学习日期。

### 2) 默写功能增强

- 开始默写前可配置默写个数（留空为全部）。
- 支持“仅默写某天已学习单词”（可选择日期）。
- 默写加载逻辑支持按“词库 + 日期”筛选，再按数量截取。

### 3) 数据层变更

- 新增表：`learned_words`（记录 `word_id + learned_date`，同一天去重）。
- 数据库版本升级：`DATABASE_VERSION = 3`，升级时自动创建新表，不清空原有数据。

### 4) 相关实现文件

- 学习页：`LearnWordsActivity.kt`
- 学习列表适配器：`LearnWordAdapter.kt`
- 学习记录 DAO：`LearnedWordDao.kt`
- 默写参数扩展：`DictationActivity.kt`
- 首页入口与配置弹窗：`MainActivity.kt`

## 更新说明（2026-04-11，补充）

### 5) 内置词库扩展

已将以下 CSV 词库内置到 App（首次建库或升级后自动导入）：

- `cet4.csv` -> 四级
- `cet6.csv` -> 六级
- `graduate.csv` -> 考研英语
- `sat.csv` -> SAT
- `toefl.csv` -> 托福
- `junior.csv` -> 初中英语
- `senior.csv` -> 高中英语

并补充了分类迁移逻辑（如 `考研` -> `考研英语`）。

### 6) 词库管理页性能优化

词库管理页已改为分页懒加载：

- 首次仅加载一页（分页查询）
- 下拉接近底部自动加载更多
- 搜索也采用分页查询
- 页面显示已加载数量 / 总数量

### 7) 已学习单词查看功能

新增“查看已学习单词”页面，支持两种方式：

- 按时间查看（选择学习日期）
- 按词库分类查看（含“全部”）

入口：主页按钮 `查看已学习单词`。
