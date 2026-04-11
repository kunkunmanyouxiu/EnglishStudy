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
