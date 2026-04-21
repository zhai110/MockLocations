package com.mockloc.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mockloc.databinding.ItemSearchHistoryBinding

/**
 * 搜索历史适配器
 */
class SearchHistoryAdapter(
    private val onItemClick: (SearchHistoryItem) -> Unit,
    private val onDeleteClick: (SearchHistoryItem) -> Unit
) : ListAdapter<SearchHistoryItem, SearchHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onDeleteClick)
    }

    class ViewHolder(private val binding: ItemSearchHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: SearchHistoryItem,
            onItemClick: (SearchHistoryItem) -> Unit,
            onDeleteClick: (SearchHistoryItem) -> Unit
        ) {
            // 显示关键词和地点名称
            binding.keywordText.text = item.keyword
            binding.nameText.text = item.name
            
            // 点击事件
            binding.root.setOnClickListener {
                onItemClick(item)
            }
            
            // 删除按钮
            binding.deleteBtn.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SearchHistoryItem>() {
        override fun areItemsTheSame(oldItem: SearchHistoryItem, newItem: SearchHistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SearchHistoryItem, newItem: SearchHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
