package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemErrorWordBinding
import com.example.myapplication.model.ErrorWord

class ErrorWordAdapter(
    private val onDelete: (ErrorWord) -> Unit
) : ListAdapter<ErrorWord, ErrorWordAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ErrorWord>() {
            override fun areItemsTheSame(a: ErrorWord, b: ErrorWord) = a.id == b.id
            override fun areContentsTheSame(a: ErrorWord, b: ErrorWord) = a == b
        }
    }

    inner class ViewHolder(private val binding: ItemErrorWordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(errorWord: ErrorWord) {
            binding.tvWord.text = errorWord.word
            binding.tvPhonetic.text = errorWord.phonetic ?: ""
            binding.tvDefinition.text = errorWord.definition
            binding.tvErrorCount.text = "错误 ${errorWord.errorCount} 次"
            binding.tvLastAnswer.text = "上次答案：${errorWord.errorAnswer}"
            binding.btnRemove.setOnClickListener { onDelete(errorWord) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemErrorWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
