package com.mockloc.util

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 统一的加载状态管理器
 * 
 * 功能：
 * 1. 管理各种加载状态的显示和隐藏
 * 2. 提供加载进度反馈
 * 3. 自动处理加载超时
 * 4. 支持多种加载场景
 */
object LoadingManager {
    
    /**
     * 显示简单加载指示器（ProgressBar）
     * 
     * @param progressBar 进度条视图
     * @param message 可选的加载提示文本
     */
    fun showLoading(progressBar: View, message: String? = null) {
        progressBar.isVisible = true
        
        // 如果有TextView显示消息
        if (message != null && progressBar is android.widget.LinearLayout) {
            val textView = progressBar.findViewById<TextView>(android.R.id.text1)
            textView?.text = message
            textView?.isVisible = true
        }
    }
    
    /**
     * 隐藏加载指示器
     */
    fun hideLoading(progressBar: View) {
        progressBar.isVisible = false
    }
    
    /**
     * 显示带进度的加载指示器
     * 
     * @param progressBar 进度条
     * @param progress 进度值 (0-100)
     * @param message 加载提示文本
     */
    fun showProgress(progressBar: ProgressBar, progress: Int, message: String? = null) {
        progressBar.isVisible = true
        progressBar.isIndeterminate = false
        progressBar.progress = progress.coerceIn(0, 100)
        
        // 更新消息（如果有）
        if (message != null) {
            val parent = progressBar.parent as? android.widget.LinearLayout
            val textView = parent?.findViewById<TextView>(android.R.id.text1)
            textView?.text = "$message ($progress%)"
        }
    }
    
    /**
     * 显示不确定进度的加载（无限循环）
     */
    fun showIndeterminateLoading(progressBar: ProgressBar, message: String? = null) {
        progressBar.isVisible = true
        progressBar.isIndeterminate = true
        
        if (message != null) {
            val parent = progressBar.parent as? android.widget.LinearLayout
            val textView = parent?.findViewById<TextView>(android.R.id.text1)
            textView?.text = message
        }
    }
    
    /**
     * 通过Snackbar显示加载提示
     * 
     * @param view 父视图
     * @param message 加载消息
     * @param duration 显示时长
     */
    fun showLoadingSnackbar(
        view: View,
        message: String,
        duration: Int = Snackbar.LENGTH_INDEFINITE
    ): Snackbar {
        val snackbar = Snackbar.make(view, "⏳ $message", duration)
        snackbar.show()
        return snackbar
    }
    
    /**
     * 显示成功提示并隐藏加载
     */
    fun showSuccessAndHideLoading(
        progressBar: View,
        context: android.content.Context,
        message: String
    ) {
        hideLoading(progressBar)
        UIFeedbackHelper.showToast(context, message)
    }
    
    /**
     * 显示错误提示并隐藏加载
     */
    fun showErrorAndHideLoading(
        progressBar: View,
        context: android.content.Context,
        message: String
    ) {
        hideLoading(progressBar)
        UIFeedbackHelper.showToast(context, message)
    }
    
    /**
     * 带超时的加载操作
     * 
     * @param progressBar 进度条
     * @param timeoutMs 超时时间（毫秒）
     * @param operation 异步操作
     * @param onSuccess 成功回调
     * @param onError 错误回调
     * @param onTimeout 超时回调
     */
    suspend fun executeWithTimeout(
        progressBar: View,
        timeoutMs: Long = 10000L,
        operation: suspend () -> kotlin.Result<Unit>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onTimeout: (() -> Unit)? = null
    ) {
        showIndeterminateLoading(progressBar as? ProgressBar ?: return)
        
        try {
            // 创建超时Job
            val result = withTimeoutOrNull(timeoutMs) {
                operation()
            }
            
            when {
                result == null -> {
                    // 超时
                    hideLoading(progressBar)
                    onTimeout?.invoke()
                    onError("操作超时，请重试")
                }
                result.isSuccess -> {
                    hideLoading(progressBar)
                    onSuccess()
                }
                else -> {
                    hideLoading(progressBar)
                    // 使用 Kotlin 标准库 Result 的 exceptionOrNull()
                    val exception = result.exceptionOrNull()
                    onError(exception?.message ?: "操作失败")
                }
            }
        } catch (e: Exception) {
            hideLoading(progressBar)
            onError(e.message ?: "未知错误")
        }
    }
    
    /**
     * 批量操作的进度管理
     */
    class BatchOperationTracker(
        private val totalItems: Int,
        private val onProgressUpdate: (Int, Int) -> Unit, // (current, total)
        private val onComplete: () -> Unit,
        private val onError: (String) -> Unit
    ) {
        private var completedItems = 0
        
        /**
         * 标记一个项目完成
         */
        fun markItemCompleted() {
            completedItems++
            val progress = (completedItems * 100 / totalItems).coerceIn(0, 100)
            onProgressUpdate(completedItems, totalItems)
            
            if (completedItems >= totalItems) {
                onComplete()
            }
        }
        
        /**
         * 获取当前进度百分比
         */
        fun getProgressPercent(): Int {
            return (completedItems * 100 / totalItems).coerceIn(0, 100)
        }
        
        /**
         * 重置追踪器
         */
        fun reset() {
            completedItems = 0
        }
    }
}
