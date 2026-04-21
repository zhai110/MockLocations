package com.mockloc.service

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mockloc.data.db.HistoryLocation
import com.mockloc.databinding.ItemHistoryBinding
import timber.log.Timber

/**
 * 悬浮窗历史记录适配器
 * 使用 RecyclerView + ListAdapter，与主应用保持一致
 */
class FloatingHistoryAdapter(
    private val onItemClick: (HistoryLocation) -> Unit,
    private val context: android.content.Context  // 添加 context 参数
) : ListAdapter<HistoryLocation, FloatingHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 使用传入的 context 而不是 parent.context，确保主题正确
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    class ViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryLocation, onClick: (HistoryLocation) -> Unit) {
            // 名称：自动适应宽度，超长显示省略号
            binding.nameText.text = item.name
            
            // 坐标：统一格式，保留4位小数
            binding.coordsText.text = String.format(
                "%.4f°N, %.4f°E",
                item.latitude,
                item.longitude
            )
            
            // 隐藏删除按钮（悬浮窗中不需要删除功能）
            binding.deleteBtn.visibility = android.view.View.GONE
            
            binding.root.setOnClickListener {
                Timber.d("FloatingHistoryAdapter: Item clicked - ${item.name}")
                onClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HistoryLocation>() {
        override fun areItemsTheSame(oldItem: HistoryLocation, newItem: HistoryLocation): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HistoryLocation, newItem: HistoryLocation): Boolean {
            return oldItem == newItem
        }
    }
}
