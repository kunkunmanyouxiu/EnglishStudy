package com.example.myapplication.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.databinding.ItemLearnWordBinding
import com.example.myapplication.model.Word

class LearnWordAdapter : RecyclerView.Adapter<LearnWordAdapter.ViewHolder>() {
    private val items = mutableListOf<Word>()
    private var hideWord = false
    private var hideDefinition = false

    inner class ViewHolder(private val binding: ItemLearnWordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(word: Word, position: Int) {
            binding.tvIndex.text = "${position + 1}."
            binding.tvWord.text = if (hideWord) "******" else word.word
            binding.tvDefinition.text = if (hideDefinition) "******" else word.definition
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLearnWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun submitWords(words: List<Word>) {
        items.clear()
        items.addAll(words)
        notifyDataSetChanged()
    }

    fun updateMaskState(maskWord: Boolean, maskDefinition: Boolean) {
        hideWord = maskWord
        hideDefinition = maskDefinition
        notifyDataSetChanged()
    }
}

