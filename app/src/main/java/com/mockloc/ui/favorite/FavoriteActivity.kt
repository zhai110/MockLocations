package com.mockloc.ui.favorite

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mockloc.data.db.FavoriteLocation
import com.mockloc.data.repository.FavoriteRepository
import com.mockloc.databinding.ActivityFavoriteBinding
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 收藏页面
 */
class FavoriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteBinding
    private lateinit var adapter: FavoriteAdapter

    // ✅ Phase 1: Repository 替代直接 DAO 访问
    private val favoriteRepository by lazy {
        val db = com.mockloc.VirtualLocationApp.getDatabase()
        FavoriteRepository(db.favoriteLocationDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = FavoriteAdapter(
            onItemClick = { item ->
                Timber.d("Favorite item clicked: ${item.name}")
                val resultIntent = android.content.Intent().apply {
                    putExtra("latitude", item.latitude)
                    putExtra("longitude", item.longitude)
                    putExtra("name", item.name)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            },
            onDeleteClick = { item ->
                showDeleteDialog(item)
            }
        )
        
        binding.favoriteList.apply {
            layoutManager = LinearLayoutManager(this@FavoriteActivity)
            adapter = this@FavoriteActivity.adapter
            
            // 添加淡入淡出动画
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = com.mockloc.util.AnimationConfig.getFadeInDuration()
                removeDuration = com.mockloc.util.AnimationConfig.getFadeOutDuration()
            }
            
            // 添加分隔线（可选，因为已经有 CardView 间距）
            // val divider = androidx.recyclerview.widget.DividerItemDecoration(
            //     this@FavoriteActivity,
            //     androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
            // )
            // addItemDecoration(divider)
        }
    }

    private fun showDeleteDialog(item: FavoriteItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除收藏")
            .setMessage("确定要删除 \"${item.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteItem(item)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteItem(item: FavoriteItem) {
        lifecycleScope.launch {
            try {
                favoriteRepository.deleteById(item.id)
                loadData()
                Timber.d("已删除: ${item.name}")
            } catch (e: Exception) {
                Timber.e(e, "删除失败")
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                // ✅ 显示加载指示器
                showLoading(true)
                
                // ✅ Phase 1: 通过 FavoriteRepository 加载
                val items = favoriteRepository.getAllFavorites()
                
                val favoriteItems = items.map {
                    FavoriteItem(it.id, it.name, it.latitude, it.longitude)
                }
                
                adapter.submitList(favoriteItems)
                updateEmptyState(favoriteItems.isEmpty())
            } catch (e: Exception) {
                Timber.e(e, "加载收藏失败")
            } finally {
                // ✅ 隐藏加载指示器
                showLoading(false)
            }
        }
    }

    /**
     * ✅ 显示/隐藏加载状态
     */
    private fun showLoading(isLoading: Boolean) {
        binding.loadingProgress.visibility = if (isLoading) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }
}

data class FavoriteItem(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
