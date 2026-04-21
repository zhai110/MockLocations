package com.mockloc.util

import android.content.Context
import android.widget.Toast
import timber.log.Timber

/**
 * 全局异常处理器
 * 
 * 功能：
 * 1. 捕获未处理的异常
 * 2. 记录详细的错误日志
 * 3. 提供用户友好的错误提示
 * 4. 可选的崩溃上报
 */
object CrashHandler {
    
    private var context: Context? = null
    private var isInitialized = false
    
    /**
     * 初始化异常处理器
     */
    fun init(appContext: Context) {
        if (isInitialized) return
        
        context = appContext.applicationContext
        
        // 设置默认的未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }
        
        isInitialized = true
        Timber.i("CrashHandler initialized")
    }
    
    /**
     * 处理未捕获的异常
     */
    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        Timber.e(throwable, "Uncaught exception in thread: ${thread.name}")
        
        // 记录崩溃信息
        logCrashInfo(throwable)
        
        // 显示用户友好的错误提示
        showUserFriendlyError(throwable)
        
        // 这里可以添加崩溃上报逻辑
        // reportCrash(throwable)
        
        // 等待一下让用户看到提示
        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
            Timber.e(e, "Interrupted while waiting")
        }
        
        // 退出应用
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(1)
    }
    
    /**
     * 记录崩溃信息
     */
    private fun logCrashInfo(throwable: Throwable) {
        val crashInfo = buildString {
            appendLine("===== CRASH REPORT =====")
            appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("Thread: ${Thread.currentThread().name}")
            appendLine("Exception: ${throwable.javaClass.simpleName}")
            appendLine("Message: ${throwable.message}")
            appendLine("Stack Trace:")
            throwable.stackTrace.forEachIndexed { index, element ->
                appendLine("  [$index] $element")
            }
            
            // 如果有原因，也记录下来
            throwable.cause?.let { cause ->
                appendLine("Caused by: ${cause.javaClass.simpleName}: ${cause.message}")
                cause.stackTrace.forEachIndexed { index, element ->
                    appendLine("  [$index] $element")
                }
            }
            appendLine("=======================")
        }
        
        Timber.e(crashInfo)
    }
    
    /**
     * 显示用户友好的错误提示
     */
    private fun showUserFriendlyError(throwable: Throwable) {
        context?.let { ctx ->
            val message = getUserFriendlyMessage(throwable)
            
            // 使用 Toast 显示（因为此时 Activity 可能已经销毁）
            Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 根据异常类型返回用户友好的消息
     */
    private fun getUserFriendlyMessage(throwable: Throwable): String {
        return when (throwable) {
            is java.net.ConnectException -> "网络连接失败，请检查网络设置"
            is java.net.SocketTimeoutException -> "请求超时，请稍后重试"
            is java.io.IOException -> "数据读取失败，请重试"
            is SecurityException -> "权限不足，请在设置中授予相关权限"
            is OutOfMemoryError -> "内存不足，请关闭其他应用后重试"
            is IllegalStateException -> "操作失败，请重启应用"
            else -> "应用遇到意外错误，即将重启"
        }
    }
    
    /**
     * 安全执行代码块，捕获并处理异常
     * 
     * @param tag 日志标签
     * @param errorMessage 错误提示消息
     * @param showToast 是否显示Toast提示
     * @param block 要执行的代码块
     * @return 执行结果，如果发生异常则返回 null
     */
    fun <T> safeExecute(
        tag: String = "SafeExecute",
        errorMessage: String = "操作失败",
        showToast: Boolean = false,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            Timber.e(e, "$tag: $errorMessage")
            if (showToast) {
                context?.let {
                    Toast.makeText(it, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
            null
        }
    }
    
    /**
     * 安全执行 suspend 函数
     */
    suspend fun <T> safeExecuteSuspend(
        tag: String = "SafeExecute",
        errorMessage: String = "操作失败",
        showToast: Boolean = false,
        block: suspend () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            Timber.e(e, "$tag: $errorMessage")
            if (showToast) {
                context?.let {
                    Toast.makeText(it, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
            null
        }
    }
    
    /**
     * 上报崩溃信息（预留接口）
     */
    private fun reportCrash(throwable: Throwable) {
        // TODO: 集成崩溃上报服务（如 Firebase Crashlytics、Bugly 等）
        // 示例：
        // FirebaseCrashlytics.getInstance().recordException(throwable)
        Timber.w("Crash reporting not implemented yet")
    }
}
