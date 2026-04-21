package com.mockloc.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mockloc.repository.PoiSearchHelper.PlaceItem
import com.mockloc.databinding.ItemSearchResultBinding
import com.mockloc.util.AnimationConfig

/**
 * 搜索结果适配器（带动画）
 */
class SearchResultAdapter(
    private val onItemClick: (PlaceItem) -> Unit
) : ListAdapter<PlaceItem, SearchResultAdapter.ViewHolder>(DiffCallback()) {

    private var lastPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PlaceItem, position: Int) {
            binding.apply {
                nameText.text = item.name
                addressText.text = item.address
                // 显示距离（周边搜索时有值）
                if (item.distance >= 0) {
                    distanceText.text = formatDistance(item.distance)
                    distanceText.visibility = View.VISIBLE
                } else {
                    distanceText.visibility = View.GONE
                }
            }
            
            // 添加渐入动画
            setAnimation(binding.root, position)
            
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
        
        /**
         * 为列表项添加渐入动画
         */
        private fun setAnimation(view: View, position: Int) {
            // 如果应该跳过动画，直接显示
            if (AnimationConfig.shouldSkipAnimation()) {
                view.apply {
                    alpha = 1f
                    translationY = 0f
                    visibility = View.VISIBLE
                }
                return
            }
            
            // 只有当位置比上次大时才播放动画（新添加的项）
            if (position > lastPosition) {
                // 初始状态：透明且向下偏移
                view.apply {
                    alpha = 0f
                    translationY = 20f
                }
                
                // 执行动画
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(AnimationConfig.getFadeInDuration())
                    .setStartDelay(position * AnimationConfig.getListItemStaggerDelay())  // 级联延迟
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        // 动画结束后重置变换
                        view.alpha = 1f
                        view.translationY = 0f
                    }
                    .start()
                
                lastPosition = position
            }
        }

        private fun formatDistance(meters: Int): String {
            return when {
                meters < 1000 -> "${meters}m"
                else -> String.format("%.1fkm", meters / 1000.0)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PlaceItem>() {
        override fun areItemsTheSame(oldItem: PlaceItem, newItem: PlaceItem): Boolean {
            return oldItem.lat == newItem.lat && oldItem.lng == newItem.lng
        }

        override fun areContentsTheSame(oldItem: PlaceItem, newItem: PlaceItem): Boolean {
            return oldItem == newItem
        }
    }
}
