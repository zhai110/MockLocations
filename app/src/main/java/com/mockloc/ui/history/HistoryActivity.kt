package com.mockloc.ui.history

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mockloc.VirtualLocationApp
import com.mockloc.data.db.HistoryLocation
import com.mockloc.data.db.SearchHistory
import com.mockloc.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 历史记录页面
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var locationAdapter: HistoryAdapter
    private lateinit var searchAdapter: SearchHistoryAdapter
    
    // 当前选中的标签：0=位置记录, 1=搜索记录
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupToggleGroup()
        setupRecyclerViews()
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

    private fun setupToggleGroup() {
        // 默认选中位置记录
        binding.toggleGroup.check(binding.tabLocation.id)
        
        // 监听标签切换
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentTab = when (checkedId) {
                    binding.tabLocation.id -> 0
                    binding.tabSearch.id -> 1
                    else -> 0
                }
                loadData()
            }
        }
    }

    private fun setupRecyclerViews() {
        // 位置记录适配器
        locationAdapter = HistoryAdapter(
            onItemClick = { item ->
                Timber.d("Location history item clicked: ${item.name}")
                val resultIntent = android.content.Intent().apply {
                    putExtra("latitude", item.latitude)
                    putExtra("longitude", item.longitude)
                    putExtra("name", item.name)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            },
            onDeleteClick = { item ->
                showDeleteLocationDialog(item)
            }
        )
        
        // 搜索历史适配器
        searchAdapter = SearchHistoryAdapter(
            onItemClick = { item ->
                Timber.d("Search history item clicked: ${item.name}")
                val resultIntent = android.content.Intent().apply {
                    putExtra("latitude", item.latitude)
                    putExtra("longitude", item.longitude)
                    putExtra("name", item.name)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            },
            onDeleteClick = { item ->
                showDeleteSearchDialog(item)
            }
        )
        
        // 配置 RecyclerView
        binding.historyList.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = locationAdapter  // 默认显示位置记录
            
            // 添加淡入淡出动画
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = com.mockloc.util.AnimationConfig.getFadeInDuration()
                removeDuration = com.mockloc.util.AnimationConfig.getFadeOutDuration()
            }
        }
    }

    private fun showDeleteLocationDialog(item: HistoryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除记录")
            .setMessage("确定要删除 \"${item.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteLocationItem(item)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteSearchDialog(item: SearchHistoryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除搜索记录")
            .setMessage("确定要删除 \"${item.keyword}\" 的搜索结果吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteSearchItem(item)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteLocationItem(item: HistoryItem) {
        lifecycleScope.launch {
            try {
                val db = VirtualLocationApp.getDatabase()
                db.historyLocationDao().deleteById(item.id)
                loadData()
                Timber.d("已删除位置记录: ${item.name}")
            } catch (e: Exception) {
                Timber.e(e, "删除位置记录失败")
            }
        }
    }

    private fun deleteSearchItem(item: SearchHistoryItem) {
        lifecycleScope.launch {
            try {
                val db = VirtualLocationApp.getDatabase()
                db.searchHistoryDao().deleteById(item.id)
                loadData()
                Timber.d("已删除搜索记录: ${item.keyword}")
            } catch (e: Exception) {
                Timber.e(e, "删除搜索记录失败")
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val db = VirtualLocationApp.getDatabase()
                Timber.d("Loading data, currentTab: $currentTab")
                
                if (currentTab == 0) {
                    // 加载位置记录
                    val items = db.historyLocationDao().getAll()
                    Timber.d("Loaded ${items.size} location history items")
                    val historyItems = items.map {
                        HistoryItem(it.id, it.name, it.latitude, it.longitude)
                    }
                    
                    // 切换到位置记录适配器
                    binding.historyList.adapter = locationAdapter
                    // 确保动画配置一致
                    binding.historyList.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                        addDuration = com.mockloc.util.AnimationConfig.getFadeInDuration()
                        removeDuration = com.mockloc.util.AnimationConfig.getFadeOutDuration()
                    }
                    locationAdapter.submitList(historyItems)
                    updateEmptyState(historyItems.isEmpty())
                } else {
                    // 加载搜索历史
                    val items = db.searchHistoryDao().getAll()
                    Timber.d("Loaded ${items.size} search history items")
                    val searchItems = items.map {
                        SearchHistoryItem(it.id, it.keyword, it.name, it.address, it.latitude, it.longitude)
                    }
                    
                    // 切换到搜索历史适配器
                    binding.historyList.adapter = searchAdapter
                    // 确保动画配置一致
                    binding.historyList.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                        addDuration = com.mockloc.util.AnimationConfig.getFadeInDuration()
                        removeDuration = com.mockloc.util.AnimationConfig.getFadeOutDuration()
                    }
                    searchAdapter.submitList(searchItems)
                    updateEmptyState(searchItems.isEmpty())
                }
            } catch (e: Exception) {
                Timber.e(e, "加载历史记录失败")
            }
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

data class HistoryItem(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class SearchHistoryItem(
    val id: Long,
    val keyword: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
