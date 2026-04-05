# 英语默写 App 开发文档

> 基于本地 AI 大模型（Gemma）的智能英语单词默写学习应用

---

## 目录

1. [项目概览](#1-项目概览)
2. [架构设计](#2-架构设计)
3. [功能模块说明](#3-功能模块说明)
4. [数据库设计](#4-数据库设计)
5. [DAO 层接口](#5-dao-层接口)
6. [数据模型](#6-数据模型)
7. [AI 模块](#7-ai-模块)
8. [艾宾浩斯算法](#8-艾宾浩斯算法)
9. [TTS 语音模块](#9-tts-语音模块)
10. [UI 布局说明](#10-ui-布局说明)
11. [权限与配置](#11-权限与配置)
12. [依赖库](#12-依赖库)
13. [核心业务流程](#13-核心业务流程)
14. [使用说明](#14-使用说明)

---

## 1. 项目概览

### 基本信息

| 项目 | 内容 |
|------|------|
| 应用名称 | 英语默写 |
| 包名 | `com.example.myapplication` |
| 编程语言 | Kotlin |
| 最低 SDK | API 26（Android 8.0） |
| 目标 SDK | API 36 |
| 数据库 | SQLite（本地，`english_study.db`） |
| AI 推理 | Google LiteRT LM（本地离线） |

### 核心特性

- **智能判题**：两阶段判题系统，精确匹配即时响应，语义模糊时调用本地 AI 大模型
- **科学复习**：基于艾宾浩斯遗忘曲线的间隔复习算法，自动推算复习时间
- **自定义词库**：支持手动添加单词、CSV 批量导入，并在导入前由 AI 预先校验匹配度
- **双向默写**：支持英文→中文、中文→英文两种模式
- **完全离线**：模型内置于 APK，数据本地存储，无网络依赖

---

## 2. 架构设计

### 目录结构

```
com.example.myapplication/
├── ui/                          # 表现层（8 个 Activity）
│   ├── MainActivity
│   ├── DictationActivity
│   ├── ReviewActivity
│   ├── WordLibraryActivity
│   ├── StatisticsActivity
│   ├── ErrorWordsActivity
│   ├── CustomBookManagerActivity
│   ├── CustomWordUploadActivity
│   └── ImportWordBookActivity
├── database/                    # 数据访问层
│   ├── DatabaseHelper           # SQLite 建表 & 初始化
│   ├── WordDao
│   ├── StudyRecordDao
│   ├── ErrorWordDao
│   └── CustomWordBookDao
├── model/                       # 数据模型层
│   ├── Word
│   ├── StudyRecord
│   ├── ErrorWord
│   ├── CustomWordBook
│   └── AiResult（含 JudgeResult、PreJudgeResult）
├── adapter/                     # RecyclerView 适配器
│   ├── WordAdapter
│   ├── ErrorWordAdapter
│   └── CustomBookAdapter
├── ai/                          # AI 推理模块
│   └── ModelManager
├── algorithm/                   # 算法模块
│   └── EbbinghausAlgorithm
└── tts/                         # 语音合成模块
    └── TTSManager
```

### 技术栈

| 技术 | 用途 |
|------|------|
| Kotlin Coroutines | 异步 IO、数据库查询 |
| ViewBinding | XML 布局与 Activity 绑定 |
| RecyclerView + ListAdapter + DiffUtil | 高效列表渲染 |
| SQLite（原生） | 本地持久化存储 |
| Google LiteRT LM 0.10.0 | 本地离线 AI 大模型推理 |
| Android TextToSpeech | 单词发音 |
| Material Design 3 | UI 设计体系 |

---

## 3. 功能模块说明

### 3.1 MainActivity — 主界面

入口页面，负责 AI 模型预加载和功能导航。

**功能：**
- 异步初始化 LiteRT LM 模型，显示加载状态（"AI 模型加载中…" / "✅ AI 模型已就绪"）
- 展示今日待复习词数和词库总量
- 点击默写按钮时，**先弹出词库选择对话框**，选定后跳转默写界面

**关键方法：**

| 方法 | 说明 |
|------|------|
| `initModelAsync()` | 后台线程初始化 AI 模型，完成后回调更新 UI |
| `startDictation(mode)` | 显示词库选择对话框，选定后启动 DictationActivity |
| `updateStats()` | 协程查询数据库更新统计数字 |

---

### 3.2 DictationActivity — 默写核心

应用最核心的页面，实现完整的单词默写与智能判题。

**两种模式：**

| 常量 | 值 | 显示内容 | 用户输入 |
|------|---|---------|---------|
| `MODE_EN_TO_CN` | 0 | 英文单词 + 音标 | 中文释义 |
| `MODE_CN_TO_EN` | 1 | 中文释义 + 例句 | 英文单词 |

**判题流程（两阶段）：**

```
用户提交答案
    │
    ▼
quickJudge() — 快速判断（无 AI，毫秒级）
    ├── 精确匹配 → 直接返回"正确"
    ├── 多义词任一义项匹配 → 直接返回"正确"
    ├── 中→英编辑距离=1 → 直接返回"部分正确"
    └── 无法判断 → 继续
    │
    ▼
modelManager.judge() — AI 语义判题
    └── 返回：正确 / 部分正确 / 错误
    │
    ▼
showResult() — 显示结果（颜色编码）
    └── saveStudyRecord() — 保存学习记录，计算下次复习时间
```

**关键方法：**

| 方法 | 说明 |
|------|------|
| `loadWords()` | 根据 `customBookId` 或 `category` 加载单词，随机打乱 |
| `quickJudge()` | 精确匹配 + 编辑距离快速判断，无需 AI |
| `editDistance()` | 计算两字符串的莱文斯坦编辑距离 |
| `submitAnswer()` | 提交答案，先走快速通道，否则调 AI |
| `showResult()` | 渲染结果卡片（颜色 + 文字 + 提示） |
| `saveStudyRecord()` | 写入学习记录，调用艾宾浩斯算法计算下次复习时间 |
| `showCategoryDialog()` | 在默写中途切换词库 |

**UI 元素：**
- 模式切换按钮、词库选择、进度显示
- 主内容区（单词 / 释义）、音标 / 例句
- 答案输入框、发音按钮、提交 / 跳过
- 判题结果卡片：绿色（正确）/ 橙色（部分正确）/ 红色（错误）

---

### 3.3 ReviewActivity — 今日复习

基于艾宾浩斯算法的智能复习，只展示当前已到复习时间的单词。

**逻辑：** 查询 `study_records` 表中 `next_review_time <= datetime('now')` 的记录，获取对应单词列表，以默��模式进行复习。

---

### 3.4 WordLibraryActivity — 词库浏览

浏览所有单词，支持按分类筛选和关键词搜索。

**功能：**
- 顶部 Chip 动态生成所有分类（标准词库 + 自定义词库）
- 搜索框实时模糊搜索（单词名 + 释义）
- 支持直接跳转到指定自定义词库（从词库管理界面传入 `custom_book_id` + `custom_book_name`）

**分类切换逻辑：**

```kotlin
// 从词库管理界面跳入时，用词库名初始化 currentCategory
customBookId = intent.getIntExtra("custom_book_id", -1)
val customBookName = intent.getStringExtra("custom_book_name")
if (customBookId != -1 && !customBookName.isNullOrBlank()) {
    currentCategory = customBookName
}

// loadWords() 优先级：customBookId > category
when {
    customBookId != -1 -> wordDao.getCustomWordsByBookId(customBookId)
    currentCategory == "全部" -> wordDao.getAllWords()
    else -> wordDao.getAllWordsByCategory(currentCategory)
}

// chip 切换时重置 customBookId
fun onCategorySelected(category: String) {
    currentCategory = category
    customBookId = -1
    loadWords()
}
```

---

### 3.5 StatisticsActivity — 学习统计

展示多维度学习数据，包含自绘柱状图。

**统计项：**

| 项目 | 数据来源 |
|------|---------|
| 词库总量 | `wordDao.getAllWords().size` |
| 自定义单词数 / 词库数 | `customBookDao.getTotalCustomWordCount()` |
| 累计学习次数 | `studyRecordDao.getTotalStudiedCount()` |
| 今日已复习词数 | `studyRecordDao.getTodayReviewCount()` |
| 错题总数 | `errorWordDao.getTotalErrorCount()` |
| 今日正确 / 部分 / 错误 | `studyRecordDao.getTodayStudyStats()` |

**柱状图：** 使用 `Canvas` + `Bitmap` 自绘，修复了 `visibility=GONE` 导致宽高为 0 崩溃的问题（先设 `INVISIBLE` 占位，`post{}` 内绘制完再设 `VISIBLE`）。

---

### 3.6 ErrorWordsActivity — 错题本

显示所有答错记录，支持移除和专项练习。

**数据：** JOIN 查询 `error_words` 和 `words` 表，显示单词、释义、错误次数、最后错误答案。

---

### 3.7 CustomBookManagerActivity — 词库管理

自定义词库的增删改操作。

**操作：**

| 操作 | 说明 |
|------|------|
| 新建词库 | 输入名称 → 写入 `custom_word_book` 和 `word_category` 两张表 |
| 重命名 | 仅更新 `custom_word_book.book_name` |
| 删除 | 软删除（`is_delete=1`），单词数据保留 |
| 查看词库 | 跳转 WordLibraryActivity，传递 `custom_book_id` + `custom_book_name` |
| 导入 CSV | 跳转 ImportWordBookActivity |

---

### 3.8 CustomWordUploadActivity — 自定义单词上传

逐条添加自定义单词，上传前由 AI 进行匹配度校验。

**工作流：**

```
填写单词信息（英文、音标、释义、例句）
    │
    ▼
AI 预判断（PreJudge）
    ├── 高匹配 → 显示"立即上传"按钮
    ├── 中匹配 → 显示优化建议 + "上传" + "忽略建议上传"
    └── 低匹配 → 禁用上传，必须修改
    │
    ▼
上传单词到指定词库
    └── 同步更新词库的 word_count
```

---

### 3.9 ImportWordBookActivity — CSV 批量导入

通过系统文件选择器导入 CSV 文件，批量添加单词到指定词库。

**CSV 格式：**

```csv
word,phonetic,definition,type
abandon,[əˈbændən],放弃；遗弃,四级
ability,[əˈbɪlɪtɪ],能力；才能,四级
```

| 列 | 说明 | 是否必填 |
|----|------|---------|
| word | 英文单词 | 必填 |
| phonetic | 音标 | 可选 |
| definition | 中文释义 | 必填 |
| type | 词库分类 | 可选（默认使用选择的词库名） |

**流程：**
1. 点击"选择 CSV 文件"调用系统文件选择器
2. 解析 CSV，跳过首行（表头）
3. 弹对话框选择导入目标词库（或新建）
4. 逐条插入，显示进度
5. 完成后同步更新 `custom_word_book.word_count`

---

## 4. 数据库设计

### 数据库基本信息

- **文件名**：`english_study.db`
- **版本**：1
- **初始数据**：5 个标准词库分类（四级、六级、考研、雅思、托福）

### 4.1 words — 单词表

```sql
CREATE TABLE IF NOT EXISTS words (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    word         TEXT    NOT NULL,
    phonetic     TEXT,
    definition   TEXT    NOT NULL,
    example      TEXT,
    word_type    TEXT,
    is_collect   INTEGER DEFAULT 0,
    is_custom    INTEGER DEFAULT 0,
    custom_book_id INTEGER DEFAULT 0,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

| 字段 | 说明 |
|------|------|
| `word` | 英文单词 |
| `phonetic` | 音标（可为空） |
| `definition` | 中文释义 |
| `example` | 例句（可为空） |
| `word_type` | 词库分类名（四级 / 六级 / … / 自定义词库名） |
| `is_collect` | 是否已收藏（0/1） |
| `is_custom` | 是否为自定义单词（0/1） |
| `custom_book_id` | 所属自定义词库 ID，标准词库为 0 |

### 4.2 study_records — 学习记录表

```sql
CREATE TABLE IF NOT EXISTS study_records (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id          INTEGER NOT NULL,
    status           INTEGER NOT NULL DEFAULT 0,
    review_count     INTEGER DEFAULT 0,
    last_review_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    next_review_time TIMESTAMP NOT NULL,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
)
```

| 字段 | 说明 |
|------|------|
| `status` | 掌握程度：0=不会，1=模糊，2=掌握 |
| `review_count` | 累计复习次数（影响艾宾浩斯间隔） |
| `next_review_time` | 下次推荐复习时间，格式 `yyyy-MM-dd HH:mm:ss` |

### 4.3 error_words — 错题表

```sql
CREATE TABLE IF NOT EXISTS error_words (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    word_id         INTEGER NOT NULL,
    error_count     INTEGER DEFAULT 1,
    last_error_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_answer    TEXT,
    FOREIGN KEY (word_id) REFERENCES words(id) ON DELETE CASCADE
)
```

### 4.4 word_category — 词库分类表

```sql
CREATE TABLE IF NOT EXISTS word_category (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    category_name TEXT    NOT NULL,
    word_count    INTEGER DEFAULT 0,
    is_custom     INTEGER DEFAULT 0
)
```

> 标准词库：`is_custom=0`；自定义词库：`is_custom=1`。  
> 创建自定义词库时，同时向此表和 `custom_word_book` 表各插入一条记录。

### 4.5 custom_word_book — 自定义词库表

```sql
CREATE TABLE IF NOT EXISTS custom_word_book (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    book_name   TEXT    NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    word_count  INTEGER DEFAULT 0,
    is_delete   INTEGER DEFAULT 0
)
```

> 删除操作为软删除（`is_delete=1`），词库内单词数据不会被清除。

---

## 5. DAO 层接口

### 5.1 WordDao

```kotlin
// 查询
fun getAllWordsByCategory(category: String): List<Word>  // 标准/自定义词库均支持
fun getAllWords(): List<Word>
fun getWordById(wordId: Int): Word?
fun searchWord(keyword: String): List<Word>              // 模糊搜索单词名+释义
fun getCustomWordsByBookId(bookId: Int): List<Word>
fun getCollectedWords(): List<Word>
fun getAllCategories(): List<String>                     // 全部分类名（含自定义）
fun getWordCountByCategory(category: String): Int

// 写入
fun addWord(word, phonetic, definition, wordType, isCustom, customBookId): Long
fun insertWord(word: Word): Long
fun updateWord(word: Word): Int
fun deleteWord(wordId: Int): Int
fun toggleCollect(wordId: Int, collect: Boolean): Int
```

**`getAllWordsByCategory` 内部逻辑：**

```
查询 word_category WHERE category_name = category
    ├── is_custom = 1 → 查 custom_word_book 获取 ID → WHERE is_custom=1 AND custom_book_id=ID
    └── is_custom = 0 → WHERE word_type=category AND is_custom=0
```

### 5.2 StudyRecordDao

```kotlin
fun insertStudyRecord(wordId: Int, status: Int, nextReviewTime: String): Long  // 存在则更新
fun updateStudyRecord(wordId: Int, status: Int, nextReviewTime: String): Int
fun getRecordByWordId(wordId: Int): StudyRecord?
fun getTodayReviewWords(): List<Int>                    // next_review_time <= now
fun getTodayStudyStats(): Triple<Int, Int, Int>         // 今日 正确/部分/错误
fun getTotalStudiedCount(): Int
fun getTodayReviewCount(): Int
```

### 5.3 ErrorWordDao

```kotlin
fun insertErrorWord(wordId: Int, errorAnswer: String): Long  // 已存在则累加 error_count
fun getAllErrorWords(): List<ErrorWord>                       // JOIN words 表
fun getErrorWordByWordId(wordId: Int): ErrorWord?
fun deleteErrorWord(wordId: Int): Int
fun getTotalErrorCount(): Int
```

### 5.4 CustomWordBookDao

```kotlin
fun createCustomWordBook(bookName: String): Long        // 同时写 word_category
fun getAllCustomWordBooks(): List<CustomWordBook>        // is_delete=0
fun getCustomWordBookById(bookId: Int): CustomWordBook?
fun getCustomWordBookByName(bookName: String): CustomWordBook?
fun updateCustomWordBookName(bookId: Int, newName: String): Int
fun deleteCustomWordBook(bookId: Int): Int               // 软删除
fun incrementWordCount(bookId: Int): Int
fun incrementWordCountByDelta(bookId: Int, delta: Int): Int
fun decrementWordCount(bookId: Int): Int
fun getTotalCustomWordCount(): Int
```

---

## 6. 数据模型

### Word

```kotlin
data class Word(
    val id: Int = 0,
    val word: String,
    val phonetic: String? = null,
    val definition: String,
    val example: String? = null,
    val wordType: String = "四级",
    val isCollect: Boolean = false,
    val isCustom: Boolean = false,
    val customBookId: Int = 0,
    val createdAt: String = ""
)
```

### StudyRecord

```kotlin
data class StudyRecord(
    val id: Int = 0,
    val wordId: Int,
    val status: Int = 0,           // 0=不会 1=模糊 2=掌握
    val reviewCount: Int = 0,
    val lastReviewTime: String = "",
    val nextReviewTime: String = ""
)
```

### ErrorWord

```kotlin
data class ErrorWord(
    val id: Int = 0,
    val wordId: Int,
    val errorCount: Int = 1,
    val lastErrorTime: String = "",
    val errorAnswer: String = "",
    // JOIN 填充字段
    val word: String = "",
    val definition: String = "",
    val phonetic: String? = null
)
```

### CustomWordBook

```kotlin
data class CustomWordBook(
    val id: Int = 0,
    val bookName: String,
    val createTime: String = "",
    val wordCount: Int = 0,
    val isDelete: Boolean = false
)
```

### AI 结果模型（AiResult.kt）

```kotlin
data class JudgeResult(
    val result: String,      // "正确" / "部分正确" / "错误"
    val definition: String,  // 标准释义
    val hint: String = ""    // 补充提示（部分正确/错误时填写）
)

data class PreJudgeResult(
    val matchLevel: String,              // "高" / "中" / "低"
    val matchExplanation: String,        // 匹配说明
    val optimizationSuggestion: String = "" // 优化建议（中/低时填写）
)
```

---

## 7. AI 模块

### ModelManager（单例）

**模型文件：** `assets/gemma-4-E4B-it.litertlm`（约 2.8 GB）  
**推理框架：** Google LiteRT LM 0.10.0  
**初始化方式：** 首次启动时从 assets 复制到内部存储（`getDir("models")`），后续直接从内部存储加载

### 初始化流程

```
initModel(onSuccess, onError)
    │
    ▼（后台线程）
copyModelFileFromAssets()
    ├── 已存在且 >100MB → 跳过复制
    └── 否则 → 16MB 分块复制（首次约 5~10 分钟）
    │
    ▼
Engine(EngineConfig(modelPath)) → engine.initialize()
    │
    ▼
engine.createConversation(ConversationConfig())
    │
    ▼
onSuccess() / onError(message)
```

### 判题接口

```kotlin
fun judge(word: String, definition: String, userInput: String, mode: Int = 0): JudgeResult
```

**Prompt 设计（极简，减少生成 token 量）：**

- 英→中（mode=0）：  
  `"英文单词「{word}」标准释义：{definition}\n用户回答：{userInput}\n语义是否一致？只回复：正确、部分正确、错误"`

- 中→英（mode=1）：  
  `"中文「{definition}」对应英文单词是「{word}」\n用户回答：{userInput}\n是否正确？只回复：正确、部分正确、错误"`

### 预判断接口

```kotlin
fun preJudge(customWord: String, customDefinition: String): PreJudgeResult
```

**用途：** 用户上传自定义单词前，判断英文单词与中文释义的语义匹配程度。

**Prompt：**

```
你是一个英语单词语义匹配审核员。判断用户上传的英文单词与中文释义是否语义匹配。
用户上传单词：{word}
用户上传释义：{definition}
...
输出格式：
匹配度等级：高/中/低
匹配说明：（不超过50字）
优化建议：（仅匹配度为中/低时填写）
```

### 性能优化

| 优化手段 | 效果 |
|---------|------|
| 快速通道（精确匹配/编辑距离）| 大部分情况下完全跳过 AI 调用，毫秒级返回 |
| 极简 Prompt | 从原来要求多行输出，缩减为只输出 2~4 个汉字，生成 token 减少约 90% |
| 单例复用 Conversation | 避免每次判题重新创建会话 |

---

## 8. 艾宾浩斯算法

### EbbinghausAlgorithm（Kotlin object 单例）

### 状态系统

```
STATUS_UNKNOWN  = 0  // 不会
STATUS_FUZZY    = 1  // 模糊  
STATUS_MASTERED = 2  // 掌握
```

### 状态转移规则

| 判题结果 | 状态变化 |
|---------|---------|
| 正确 | status + 1（上限为 2） |
| 部分正确 | 固定设为 STATUS_FUZZY (1) |
| 错误 | status - 1（下限为 0） |

### 复习间隔表

| 状态 | 间隔序列（分钟） |
|------|----------------|
| 不会 (0) | 60 → 1440 → 4320 → 10080 → 20160 → 43200 |
| 模糊 (1) | 720 → 2880 → 7200 → 14400 → 28800 |
| 掌握 (2) | 10080 → 20160 → 43200 → 86400 |

> 复习次数超出序列长度时，取序列最后一个值（长期维持）。

### 关键方法

```kotlin
// 根据判题结果更新状态
fun judgeResultToStatus(judgeResult: String, currentStatus: Int): Int

// 计算下次复习时间（返回 yyyy-MM-dd HH:mm:ss 格式）
fun calculateNextReviewTime(status: Int, reviewCount: Int): String

// 格式化剩余时间（如"3天后"、"已到期"）
fun formatDate(dateStr: String): String
```

---

## 9. TTS 语音模块

### TTSManager

封装 Android 系统 `TextToSpeech`，用于单词朗读。

```kotlin
fun speak(text: String)               // 朗读文本
fun setSpeechRate(rate: Float)        // 设置语速（推荐 0.5~1.5）
fun setLocaleUS() / setLocaleUK()     // 切换美式/英式发音
fun stop()                            // 停止朗读
fun shutdown()                        // 释放资源（在 onDestroy 中调用）
```

**默认配置：** 语言 `Locale.US`，语速 `0.9f`，音调 `1.0f`

---

## 10. UI 布局说明

所有 Activity 的根 View 均设置了 `android:fitsSystemWindows="true"`，确保内容区域自动避开状态栏和刘海屏区域。

### 主题配置（themes.xml）

```xml
<style name="Theme.MyApplication" parent="Theme.MaterialComponents.Light.NoActionBar">
    <item name="colorPrimary">#1976D2</item>
    <item name="colorSecondary">#F57C00</item>
    <item name="android:statusBarColor">#1565C0</item>
</style>
```

### 布局文件一览

| 布局文件 | 对应 Activity | 根 View |
|---------|--------------|---------|
| `activity_main.xml` | MainActivity | ScrollView |
| `activity_dictation.xml` | DictationActivity | LinearLayout |
| `activity_review.xml` | ReviewActivity | ScrollView |
| `activity_word_library.xml` | WordLibraryActivity | LinearLayout |
| `activity_statistics.xml` | StatisticsActivity | ScrollView |
| `activity_error_words.xml` | ErrorWordsActivity | LinearLayout |
| `activity_custom_book_manager.xml` | CustomBookManagerActivity | LinearLayout |
| `activity_custom_word_upload.xml` | CustomWordUploadActivity | ScrollView |
| `activity_import_word_book.xml` | ImportWordBookActivity | LinearLayout |
| `item_word.xml` | WordAdapter | CardView |
| `item_error_word.xml` | ErrorWordAdapter | CardView |
| `item_custom_book.xml` | CustomBookAdapter | CardView |

---

## 11. 权限与配置

### AndroidManifest.xml 权限

```xml
<!-- 读取外部存储（API ≤ 32） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- 写入外部存储（API ≤ 29） -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />

<!-- 相机（可选，预留拍照识别功能） -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

### 关键应用配置

```xml
<application
    android:largeHeap="true"   <!-- 启用大堆内存，支持 AI 模型加载 -->
    android:theme="@style/Theme.MyApplication">
```

### Activity 配置

| Activity | exported | windowSoftInputMode |
|---------|----------|-------------------|
| MainActivity | true（LAUNCHER） | adjustResize |
| DictationActivity | false | adjustResize |
| CustomWordUploadActivity | false | adjustResize |
| ReviewActivity | false | adjustResize |
| 其余 Activity | false | 默认 |

---

## 12. 依赖库

```kotlin
// AI 推理（核心）
implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")

// 异步
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// 生命周期
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

// UI
implementation(libs.androidx.appcompat)
implementation(libs.material)
implementation(libs.androidx.constraintlayout)
implementation("androidx.recyclerview:recyclerview:1.3.2")
implementation("androidx.cardview:cardview:1.0.0")
implementation("androidx.viewpager2:viewpager2:1.0.0")
```

### build.gradle 特殊配置

```kotlin
androidResources {
    // 防止 AI 模型文件被压缩（避免构建卡死）
    noCompress += listOf("gguf", "bin", "tflite", "litertlm")
}
```

> **重要：** 模型文件 `gemma-4-E4B-it.litertlm`（约 2.8 GB）放置于 `app/src/main/assets/`。  
> 首次安装时 ADB 传输耗时较长（USB 2.0 约 15~30 分钟），属正常现象。  
> 首次启动后模型会被复制到内部存储，后续运行无需重复复制。

---

## 13. 核心业务流程

### 13.1 首次使用

```
安装 APK（等待 ADB 传输 ~2.8GB）
    │
    ▼
启动 APP
    │
    ▼
MainActivity.onCreate()
    ├── 后台线程：复制模型文件（assets → 内部存储，约 5~10 分钟）
    │   └── 完成后显示"✅ AI 模型已就绪"
    └── 主线程：渲染 UI，统计显示 0
    │
    ▼
用户点击默写按钮 → 弹出词库选择（此时词库为空，需先导入）
```

### 13.2 导入词库

```
方式一：CSV 批量导入
    词库管理 → 导入 → 选择 CSV 文件 → 选择/新建目标词库 → 导入

方式二：逐条添加
    词库管理 → 自定义词库 → 选择词库 → 词库详情 → 自定义上传 → 填写单词信息 → AI 预判断 → 上传
```

### 13.3 默写流程

```
主界面 → 选择模式（英→中 / 中→英）
    │
    ▼
弹出词库选择对话框 → 选择词库
    │
    ▼
DictationActivity：加载单词，随机打乱
    │
    ▼
循环：显示题目 → 输入答案 → 提交
    │
    ▼
判题（快速 → AI）→ 显示结果（颜色编码）
    │
    ├── 正确/部分正确：3 秒后自动跳下一题
    └── 错误：显示"重新默写"按钮
    │
    ▼
后台记录：写 study_records，计算下次复习时间
         若错误：写 error_words
    │
    ▼
全部完成 → 弹出完成对话框（再来一轮 / 返回）
```

### 13.4 智能复习

```
主界面 → 今日复习
    │
    ▼
ReviewActivity：查询 next_review_time <= 当前时间
    │
    ├── 有待复习词 → 启动默写（同上）
    └── 无待复习词 → 提示"今日复习已完成"
```

---

## 14. 使用说明

### 安装要求

- Android 8.0（API 26）及以上
- 存储空间：至少 **6 GB**（APK 约 3 GB + 内部存储复制约 3 GB）
- 内存：建议 **4 GB RAM** 以上（AI 模型推理需要较大内存）

### 首次启动注意

1. 首次安装时 APK 较大，请通过 USB 安装或等待较长下载时间
2. 首次启动时，应用会自动将 AI 模型复制到内部存储，页面显示"AI 模型加载中"，**请等待约 5~10 分钟**，期间可正常使用词库管理、导入等功能
3. 模型加载完成后，状态栏显示"✅ AI 模型已就绪"，此后可进行智能默写

### 添加单词

**方式一：CSV 批量导入（推荐）**

1. 准备 CSV 文件，格式如下（UTF-8 编码）：
   ```
   word,phonetic,definition,type
   abandon,[əˈbændən],放弃；遗弃,四级
   ```
2. 主界面 → **自定义词库** → **导入**
3. 选择 CSV 文件，选择或新建目标词库
4. 点击"开始导入"

**方式二：逐条手动添加**

1. 主界面 → **自定义词库** → 选择词库 → 进入词库详情
2. 点击"自定义上传"或右下角 + 按钮
3. 填写单词、音标、释义、例句
4. 点击"AI 预判断"检查匹配度
5. 通过校验后点击"立即上传"

### 开始默写

1. 主界面点击"英文→中文默写"或"中文→英文默写"
2. 在弹出的词库选择对话框中选择目标词库
3. 输入答案后点击"提交"
4. 查看判题结果，错误答案可点击"重新默写"

### 查看统计

主界面 → **学习统计**：查看累计学习数据和今日默写分布图

### 错题本

主界面 → **错题本**：查看所有答错记录，可进行错题专项练习

---

## 附录：已知问题与修复记录

| 问题 | 根本原因 | 修复方案 |
|------|---------|---------|
| 自定义词库单词无法显示 | `WordLibraryActivity.loadWords()` 未使用传入的 `customBookId` | 增加 `customBookId != -1` 优先分支 |
| 默写词库为空（自定义词库） | `WordDao.getAllWordsByCategory()` 使用 `dbHelper.context`，API < 30 运行崩溃 | 改为在 `WordDao` 中保存 `private val context` |
| 学习统计界面崩溃 | `chartView` 初始为 `GONE`，`post{}` 中读取宽高为 0，`Bitmap.createBitmap(0,0,…)` 抛异常 | 绘制前先设为 `INVISIBLE`，`post{}` 后设为 `VISIBLE` |
| 默写功能直接进入无法选词库 | 默写入口硬编码 `category="四级"`，数据库无示例数据 | 改为先弹词库选择对话框 |
| APP 安装时"一直显示 Launching" | `.litertlm` 文件未加入 `noCompress` 导致构建时尝试压缩 2.8 GB 文件 | `noCompress += "litertlm"` |
| AI 判题速度慢 | Prompt 过长，要求模型输出多行 | 极简 Prompt + 快速精确匹配通道 |
