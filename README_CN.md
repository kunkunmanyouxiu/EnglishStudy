# 📱 EnglishStudy 项目 - LiteRT LM 完整指南

## ✅ 项目状态

- ✅ 所有 Kotlin 代码已编写
- ✅ 所有 XML 布局已完成
- ✅ LiteRT LM 集成已实现
- ✅ Coroutine 内存泄漏已修复
- ✅ 模型文件验证机制已添加

---

## 🚀 快速启动

### 1. 模型文件配置 (最重要！)

**文件位置**：`app/src/main/assets/gemma-4-E4B-it.litertlm`

**文件需求**：
- 格式：LiteRT LM 模型文件
- 大小：约 2.8 GB
- 必须是有效的 zip 文件
 
**验证模型文件**（在项目目录运行）：
```bash
# 检查文件是否是有效的 LiteRT 模型
file app/src/main/assets/gemma-4-E4B-it.litertlm
# 应输出：Zip archive data

# 验证文件完整性
unzip -t app/src/main/assets/gemma-4-E4B-it.litertlm | tail -5
# 最后一行应是：All files ok!
```

### 2. 编译项目
```bash
cd d:/project/Android/EnglishStudy
./gradlew clean build
```

### 3. 运行应用
- 需要至少 2GB 内存，建议 4GB+
- 首次启动会自动从 assets 复制模型文件到内部存储（耗时 5-10 分钟，2.8GB）
- 之后启动直接加载，大约 1-2 秒

---

## 📋 项目结构

```
app/src/main/
├── java/com/example/myapplication/
│   ├── ai/
│   │   └── ModelManager.kt          ← LiteRT LM 引擎管理
│   ├── algorithm/
│   │   └── EbbinghausAlgorithm.kt   ← 艾宾浩斯复习算法
│   ├── database/
│   │   ├── DatabaseHelper.kt
│   │   ├── WordDao.kt
│   │   ├── StudyRecordDao.kt
│   │   ├── ErrorWordDao.kt
│   │   └── CustomWordBookDao.kt
│   ├── ui/
│   │   ├── MainActivity.kt
│   │   ├── DictationActivity.kt     ← 核心默写页面
│   │   ├── ReviewActivity.kt
│   │   ├── CustomWordUploadActivity.kt
│   │   ├── CustomBookManagerActivity.kt
│   │   ├── WordLibraryActivity.kt
│   │   ├── ErrorWordsActivity.kt
│   │   └── StatisticsActivity.kt
│   ├── adapter/
│   │   ├── WordAdapter.kt
│   │   ├── CustomBookAdapter.kt
│   │   └── ErrorWordAdapter.kt
│   ├── tts/
│   │   └── TTSManager.kt            ← 本地文本转语音
│   └── model/
│       ├── Word.kt
│       ├── StudyRecord.kt
│       ├── ErrorWord.kt
│       ├── CustomWordBook.kt
│       └── AiResult.kt
├── res/
│   ├── layout/                      ← 8 个 activity + 3 个 item 布局
│   ├── values/strings.xml           ← 字符串资源
│   └── ...
└── assets/
    └── gemma-4-E4B-it.litertlm      ← ⭐️ 必须放在这里
```

---

## 🔧 核心组件说明

### ModelManager (AI 判题核心)

```kotlin
// 初始化（异步）
ModelManager.getInstance(context).initModel(
    onSuccess = { /* 模型加载成功 */ },
    onError = { msg -> /* 加载失败处理 */ }
)

// AI 判题（调用 LiteRT LM）
val result = modelManager.judge(
    word = "abandon",
    definition = "放弃；遗弃",
    userInput = "放弃"  // 用户输入
)
// 返回：JudgeResult(result="正确", definition="...", hint="")

// AI 预判断（上传前验证单词与释义的匹配度）
val preResult = modelManager.preJudge(
    customWord = "abandon",
    customDefinition = "放弃；遗弃"
)
// 返回：PreJudgeResult(matchLevel="高", matchExplanation="...", optimizationSuggestion="")
```

### 数据流向

```
用户输入 → DictationActivity
       → ModelManager.judge() 在 IO 线程调用
       → LiteRT LM 处理（sendMessage）
       → 解析返回值（"正确/部分正确/错误"）
       → 更新 UI（Main 线程）
       → 保存学习记录到 SQLite
       → 计算下次复习时间（艾宾浩斯算法）
```

---

## ⚠️ 常见问题

### Q: 模型加载失败 "Unable to open zip archive"
**A:** 模型文件无效或不完整
- ✅ 验证文件是否是 zip 格式（上面有验证命令）
- ✅ 确认文件大小 > 2GB
- ✅ 删除旧文件：`rm -rf app/build` 然后重新编译

### Q: 应用启动时 FC（崩溃）
**A:** 可能原因
1. 模型文件不存在 → 检查 `assets/gemma-4-E4B-it.litertlm`
2. 权限问题 → 检查 `AndroidManifest.xml` 权限配置
3. 内存不足 → 设备需要 ≥ 2GB RAM

### Q: 按钮点击无响应
**A:** 检查
1. 模型是否加载完成（看 MainActivity 中的状态）
2. Logcat 中是否有错误信息

---

## 📊 主要功能

| 功能 | 实现状态 | 说明 |
|------|---------|------|
| 英→中默写 | ✅ | 展示英文单词，用户输入中文释义 |
| 中→英默写 | ✅ | 展示中文释义，用户输入英文单词 |
| AI 判题 | ✅ | 使用 LiteRT LM 语义判断 |
| 艾宾浩斯复习 | ✅ | 自动生成复习计划 |
| 自定义单词上传 | ✅ | 支持手动输入或拍照识别 |
| AI 预判断 | ✅ | 上传前验证单词与释义匹配度 |
| 错题本 | ✅ | 自动记录错误单词 |
| 学习统计 | ✅ | 本地绘制柱状图 |
| 本地 TTS | ✅ | Android 原生文本转语音 |
| 离线工作 | ✅ | 零网络请求 |
| 数据隐私 | ✅ | 所有数据本地 SQLite |

---

## 📝 学习记录

模型加载后会自动在 Logcat 中打印：
```
I/ModelManager: 🚀 开始初始化 LiteRT LM 模型...
I/ModelManager: 📦 模型文件大小: 2800 MB
I/ModelManager: ⏳ 正在加载模型引擎...
I/ModelManager: ✅ 模型引擎创建成功
I/ModelManager: ✅ 引擎初始化成功
I/ModelManager: ✅ 对话会话创建成功
I/ModelManager: ✅✅✅ LiteRT LM 模型加载完成！
```

**判题 Logcat**：
```
D/ModelManager: 📝 发送判题 Prompt...
D/ModelManager: 📤 模型返回: 结果: 正确...标准释义: ...
```

---

## 🔐 数据库

所有数据存储在 SQLite：
- **词库**：30 个示范四级单词 + 用户自定义单词
- **学习记录**：每个单词的学习状态、复习时间
- **错题本**：用户答错的单词
- **自定义词库**：用户创建的词库分类

**数据位置**：`/data/data/com.example.myapplication/databases/english_study.db`

---

## 🎯 下一步优化建议

1. **模型优化**：考虑使用更小的 Gemma 量化版本（600MB）
2. **拍照识别**：集成 OCR 可选功能（需要 ML Kit）
3. **导入/导出**：支持 Excel 格式词库导入
4. **数据同步**：可选的云端备份（需要用户同意）
5. **性能**：考虑 NDK 加速计算

---

## 📞 调试技巧

**查看详细日志**：
```bash
adb logcat | grep -E "ModelManager|DictationActivity|DatabaseHelper"
```

**查看数据库**：
```bash
adb shell
sqlite3 /data/data/com.example.myapplication/databases/english_study.db
.schema
SELECT COUNT(*) FROM words;
```

**清除应用数据**：
```bash
adb shell pm clear com.example.myapplication
```

---

**项目完成日期**：2025-04-05
**最后更新**：支持 LiteRT LM 模型集成
