package com.mockloc.ui.main.delegate

import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mockloc.R
import com.mockloc.databinding.FragmentMainBinding
import com.mockloc.repository.PoiSearchHelper
import com.mockloc.ui.main.MainViewModel
import com.mockloc.ui.search.SearchResultAdapter
import com.mockloc.util.AnimationHelper
import timber.log.Timber

/**
 * 搜索功能委托类
 *
 * 职责：
 * - 初始化搜索 UI（RecyclerView、清除按钮）
 * - 管理搜索结果列表的显示/隐藏
 * - 处理搜索框文本变化和清除操作
 * - 响应 ViewModel 的搜索结果更新
 *
 * 与 ViewModel 的交互方式：
 * - viewModel.searchPlaces(query, lat, lng)：发起 POI 搜索请求
 * - viewModel.selectSearchResult(poi)：选中某条搜索结果，由 ViewModel 更新地图标记位置
 * - viewModel.hideSearchResults()：隐藏搜索结果列表，由 ViewModel 通知 UI 层清除搜索状态
 *
 * 设计说明：
 * - 本 Delegate 不直接持有 AMap 引用，避免与地图生命周期耦合
 * - 搜索中心点通过 onGetSearchCenter 回调获取，由 MainFragment 在回调中返回当前地图中心
 */
class SearchDelegate(
    private val fragment: Fragment,
    private val viewModel: MainViewModel,
    private val binding: FragmentMainBinding
) {
    
    private lateinit var searchAdapter: SearchResultAdapter
    private var isSearchResultVisible = false
    
    // ✅ 搜索框文本监听器（用于清除时临时移除）
    private val searchTextWatcher = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            updateClearButtonVisibility()
        }
    }
    
    /**
     * 初始化搜索功能
     */
    fun init() {
        initSearchAdapter()
        setupSearchListeners()
    }
    
    /**
     * 初始化搜索适配器
     */
    private fun initSearchAdapter() {
        searchAdapter = SearchResultAdapter { poi ->
            // 点击搜索结果
            viewModel.selectSearchResult(poi)
            binding.searchEdit.setText(poi.name)
            binding.searchEdit.setSelection(binding.searchEdit.text.length)
        }
        
        binding.searchResultList.apply {
            layoutManager = LinearLayoutManager(fragment.requireContext())
            adapter = searchAdapter
        }
    }
    
    /**
     * 设置搜索相关监听器
     */
    private fun setupSearchListeners() {
        // 搜索框 - 键盘搜索按钮
        binding.searchEdit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEdit.text.toString()
                if (query.isNotEmpty()) {
                    // 获取搜索中心点（优先使用 ViewModel 的 currentLocation，否则通过回调获取）
                    val center = viewModel.mapState.value.currentLocation ?: onGetSearchCenter?.invoke()
                    
                    if (center != null) {
                        viewModel.searchPlaces(query, center.latitude, center.longitude)
                        // 搜索后隐藏键盘
                        hideKeyboard()
                    } else {
                        // 无法获取中心点，显示提示
                        com.mockloc.util.UIFeedbackHelper.showToast(
                            fragment.requireContext(),
                            "无法获取当前位置"
                        )
                    }
                }
                true
            } else {
                false
            }
        }
        
        // 搜索清除按钮
        binding.searchClearBtn.setOnClickListener {
            clearSearch()
        }
        
        // 监听输入框变化，动态显示/隐藏清除按钮
        binding.searchEdit.addTextChangedListener(searchTextWatcher)
        
        // 搜索框焦点变化
        binding.searchEdit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.hideSearchResults()
            } else {
                val textLength = binding.searchEdit.text.length
                if (textLength > 0) {
                    binding.searchEdit.setSelection(textLength)
                }
            }
        }
    }
    
    /**
     * 获取搜索中心点的回调（由 MainFragment 提供）
     *
     * 设计原因：Delegate 不直接持有 AMap 引用，无法自行获取地图中心点。
     * 当 ViewModel 的 mapState 中没有 currentLocation 时，通过此回调
     * 从 MainFragment 获取地图当前中心坐标作为搜索中心点。
     *
     * @return 地图中心点的 LatLng，若地图未就绪则返回 null
     */
    var onGetSearchCenter: (() -> com.amap.api.maps.model.LatLng?)? = null
    
    /**
     * 隐藏键盘
     */
    private fun hideKeyboard() {
        val imm = fragment.requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(binding.searchEdit.windowToken, 0)
    }
    
    /**
     * 获取搜索适配器（供外部使用）
     */
    fun getSearchAdapter(): SearchResultAdapter = searchAdapter
    
    /**
     * 更新搜索结果列表
     */
    fun updateResults(results: List<PoiSearchHelper.PlaceItem>) {
        searchAdapter.submitList(results)
        if (results.isNotEmpty()) {
            showSearchResults()
        } else {
            hideSearchResults()
        }
    }
    
    /**
     * 显示搜索结果列表
     */
    private fun showSearchResults() {
        if (!isSearchResultVisible) {
            binding.searchResultContainer.visibility = View.VISIBLE
            binding.searchResultList.animate().cancel()
            AnimationHelper.fadeIn(binding.searchResultList, 250)
            isSearchResultVisible = true
            // 为搜索结果列表添加底部圆角，与搜索框保持一致
            val radius = 16f * fragment.resources.displayMetrics.density
            binding.searchResultList.background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(fragment.requireContext(), R.color.surface))
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
            }
        }
    }
    
    /**
     * 隐藏搜索结果列表
     */
    fun hideSearchResults() {
        if (isSearchResultVisible) {
            binding.searchResultList.animate().cancel()
            AnimationHelper.fadeOut(binding.searchResultList, 200) {
                binding.searchResultContainer.visibility = View.GONE
            }
            isSearchResultVisible = false
        }
    }
    
    /**
     * 清除搜索（清空输入框、隐藏结果、隐藏清除按钮）
     */
    private fun clearSearch() {
        // 先移除监听器，避免 setText 触发 TextWatcher
        binding.searchEdit.removeTextChangedListener(searchTextWatcher)
        
        binding.searchEdit.setText("")
        binding.searchEdit.clearFocus()
        viewModel.hideSearchResults()
        
        // 手动更新按钮状态
        binding.searchClearBtn.visibility = View.GONE
        
        // 重新添加监听器
        binding.searchEdit.addTextChangedListener(searchTextWatcher)
        
        com.mockloc.util.UIFeedbackHelper.showToast(fragment.requireContext(), fragment.getString(R.string.toast_search_cleared))
    }
    
    /**
     * 更新清除按钮的可见性（供 MainFragment 调用）
     */
    fun updateClearButtonVisibility() {
        val hasText = binding.searchEdit.text.isNotEmpty()
        val hasResults = isSearchResultVisible
        
        // 当有输入内容或有搜索结果时显示清除按钮
        binding.searchClearBtn.visibility = if (hasText || hasResults) View.VISIBLE else View.GONE
    }
}
