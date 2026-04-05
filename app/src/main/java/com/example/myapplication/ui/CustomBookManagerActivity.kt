package com.example.myapplication.ui

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.adapter.CustomBookAdapter
import com.example.myapplication.database.CustomWordBookDao
import com.example.myapplication.database.WordDao
import com.example.myapplication.databinding.ActivityCustomBookManagerBinding
import com.example.myapplication.model.CustomWordBook
import kotlinx.coroutines.*

class CustomBookManagerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CustomBookMgrActivity"
    }

    private lateinit var binding: ActivityCustomBookManagerBinding
    private val customBookDao by lazy { CustomWordBookDao(this) }
    private val wordDao by lazy { WordDao(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 延迟初始化 adapter，确保 this 已完全初始化
    private lateinit var adapter: CustomBookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomBookManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 在 onCreate 中初始化 adapter
        adapter = CustomBookAdapter(
            onRename = { book -> showRenameDialog(book.id, book.bookName) },
            onDelete = { book -> confirmDelete(book.id, book.bookName) },
            onClick = { book ->
                val intent = android.content.Intent(this, WordLibraryActivity::class.java)
                intent.putExtra("custom_book_id", book.id)
                intent.putExtra("custom_book_name", book.bookName)
                startActivity(intent)
            }
        )

        binding.rvBooks.layoutManager = LinearLayoutManager(this)
        binding.rvBooks.adapter = adapter

        binding.btnCreateBook.setOnClickListener { showCreateDialog() }
        binding.btnImportBook.setOnClickListener { startActivity(android.content.Intent(this, ImportWordBookActivity::class.java)) }

        loadBooks()
    }

    private fun loadBooks() {
        Log.d(TAG, "📚 开始加载自定义词库...")
        scope.launch(Dispatchers.IO) {
            val books = customBookDao.getAllCustomWordBooks()
            Log.d(TAG, "📊 从数据库获取 ${books.size} 个词库")
            withContext(Dispatchers.Main) {
                // DAO 方法保证返回非 null List
                Log.d(TAG, "📋 提交列表给适配器: ${books.size} 个词库")
                adapter.submitList(books)
                binding.tvBookCount.text = "自定义词库: ${books.size} 个"
                Log.d(TAG, "✅ UI 更新完成")
            }
        }
    }

    private fun showCreateDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "输入词库名称"
        }
        AlertDialog.Builder(this)
            .setTitle("创建自定义词库")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "词库名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                scope.launch(Dispatchers.IO) {
                    customBookDao.createCustomWordBook(name)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CustomBookManagerActivity, "词库创建成功", Toast.LENGTH_SHORT).show()
                        loadBooks()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRenameDialog(bookId: Int, oldName: String) {
        val editText = android.widget.EditText(this).apply {
            setText(oldName)
        }
        AlertDialog.Builder(this)
            .setTitle("修改词库名称")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isBlank()) return@setPositiveButton
                scope.launch(Dispatchers.IO) {
                    customBookDao.updateCustomWordBookName(bookId, newName)
                    withContext(Dispatchers.Main) { loadBooks() }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDelete(bookId: Int, bookName: String) {
        AlertDialog.Builder(this)
            .setTitle("删除词库")
            .setMessage("确定要删除词库「$bookName」吗？\n（词库内的单词不会被删除）")
            .setPositiveButton("删除") { _, _ ->
                scope.launch(Dispatchers.IO) {
                    customBookDao.deleteCustomWordBook(bookId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CustomBookManagerActivity, "词库已删除", Toast.LENGTH_SHORT).show()
                        loadBooks()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 Activity 恢复，重新加载词库列表")
        loadBooks()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

