package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemWordBinding
import com.example.myapplication.model.Word

class WordAdapter : ListAdapter<Word, WordAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Word>() {
            override fun areItemsTheSame(oldItem: Word, newItem: Word) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Word, newItem: Word) = oldItem == newItem
        }
    }

    inner class ViewHolder(private val binding: ItemWordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(word: Word) {
            binding.tvWord.text = word.word
            binding.tvPhonetic.text = word.phonetic ?: ""
            binding.tvDefinition.text = word.definition
            binding.tvType.text = if (word.isCustom) "自定义" else word.wordType
            binding.ivCollect.isSelected = word.isCollect
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
