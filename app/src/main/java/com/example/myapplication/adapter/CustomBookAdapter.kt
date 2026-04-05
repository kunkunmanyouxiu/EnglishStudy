package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemCustomBookBinding
import com.example.myapplication.model.CustomWordBook

class CustomBookAdapter(
    private val onRename: (CustomWordBook) -> Unit,
    private val onDelete: (CustomWordBook) -> Unit,
    private val onClick: (CustomWordBook) -> Unit
) : ListAdapter<CustomWordBook, CustomBookAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CustomWordBook>() {
            override fun areItemsTheSame(a: CustomWordBook, b: CustomWordBook) = a.id == b.id
            override fun areContentsTheSame(a: CustomWordBook, b: CustomWordBook) = a == b
        }
    }

    inner class ViewHolder(private val binding: ItemCustomBookBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(book: CustomWordBook) {
            binding.tvBookName.text = book.bookName
            binding.tvWordCount.text = "${book.wordCount} 个单词"
            binding.tvCreateTime.text = "创建于 ${book.createTime.take(10)}"
            binding.btnRename.setOnClickListener { onRename(book) }
            binding.btnDelete.setOnClickListener { onDelete(book) }
            binding.root.setOnClickListener { onClick(book) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCustomBookBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
