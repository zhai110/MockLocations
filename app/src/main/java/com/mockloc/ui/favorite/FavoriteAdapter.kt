package com.mockloc.ui.favorite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mockloc.R
import com.mockloc.databinding.ItemHistoryBinding

/**
 * 收藏适配器
 */
class FavoriteAdapter(
    private val onItemClick: (FavoriteItem) -> Unit,
    private val onDeleteClick: (FavoriteItem) -> Unit
) : ListAdapter<FavoriteItem, FavoriteAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onDeleteClick)
    }

    class ViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FavoriteItem, onClick: (FavoriteItem) -> Unit, onDelete: (FavoriteItem) -> Unit) {
            // ✅ 关键修复：每次绑定时都从最新的 resources 和 theme 获取颜色
            // 确保主题切换后颜色正确更新
            val resources = binding.root.context.resources
            val theme = binding.root.context.theme
            
            binding.nameText.text = item.name
            binding.nameText.setTextColor(resources.getColor(R.color.text_primary, theme))
            
            binding.coordsText.text = String.format(
                "%.4f°N, %.4f°E",
                item.latitude,
                item.longitude
            )
            binding.coordsText.setTextColor(resources.getColor(R.color.text_hint, theme))
            
            binding.root.setOnClickListener {
                onClick(item)
            }
            
            binding.deleteBtn.setOnClickListener {
                onDelete(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FavoriteItem>() {
        override fun areItemsTheSame(oldItem: FavoriteItem, newItem: FavoriteItem): Boolean {
            return oldItem.latitude == newItem.latitude && oldItem.longitude == newItem.longitude
        }

        override fun areContentsTheSame(oldItem: FavoriteItem, newItem: FavoriteItem): Boolean {
            return oldItem == newItem
        }
    }
}
