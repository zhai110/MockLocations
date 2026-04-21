package com.mockloc.util

import android.content.Context
import android.os.Handler
import android.os.Looper
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
 * 
 * 安全设计：
 * - 保存原始 UncaughtExceptionHandler，记录日志后委托给原始处理器处理
 * - 不在异常处理线程中调用 Thread.sleep（避免多线程崩溃时死锁）
 * - 不直接调用 Process.killProcess/System.exit（避免跳过清理逻辑）
 * - Toast 通过主线程 Handler 显示（异常处理线程没有 Looper）
 * - appContext 强制使用 applicationContext（防止内存泄漏）
 */
object CrashHandler {
    
    // 强制使用 applicationContext，防止 Activity Context 导致内存泄漏
    private var appContext: Context? = null
    private var isInitialized = false
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    
    /**
     * 初始化异常处理器
     */
    fun init(appContext: Context) {
        if (isInitialized) return
        
        // 强制使用 applicationContext，即使传入的是 Activity Context
        this.appContext = appContext.applicationContext
        
        // 保存原始的未捕获异常处理器（通常是 Android Runtime 的默认处理器）
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        // 设置自定义的未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }
        
        isInitialized = true
        Timber.i("CrashHandler initialized")
    }
    
    /**
     * 处理未捕获的异常
     * 
     * 关键安全措施：
     * 1. 先记录日志（Timber 是轻量级的，不会阻塞）
     * 2. 尝试在主线程显示 Toast 提示（不阻塞异常处理线程）
     * 3. 委托给原始 UncaughtExceptionHandler 处理（让系统正常终止进程）
     * 
     * 不使用 Thread.sleep：在异常处理线程中 sleep 是危险的，
     * 如果其他线程也崩溃，会导致死锁（异常处理线程被 sleep 占用，无法处理新异常）
     * 
     * 不使用 Process.killProcess/System.exit：这会跳过系统的清理逻辑，
     * 委托给原始处理器可以让系统正常终止进程并显示崩溃对话框
     */
    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        // 1. 记录崩溃信息
        Timber.e(throwable, "Uncaught exception in thread: ${thread.name}")
        logCrashInfo(throwable)
        
        // 2. 尝试在主线程显示用户友好的错误提示
        //    不在异常处理线程中直接显示 Toast（该线程可能没有 Looper）
        //    也不使用 Thread.sleep 等待 Toast 显示（避免死锁风险）
        showUserFriendlyError(throwable)
        
        // 3. 委托给原始的未捕获异常处理器
        //    Android Runtime 的默认处理器会：
        //    - 打印崩溃堆栈到 logcat
        //    - 显示"应用已停止运行"对话框
        //    - 正常终止进程
        defaultHandler?.uncaughtException(thread, throwable)
            ?: // 如果没有原始处理器（不应该发生），让系统默认处理
            run { android.os.Process.killProcess(android.os.Process.myPid()) }
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
     * 
     * 通过主线程 Handler post Toast，避免在异常处理线程中直接操作 UI。
     * Toast 会在主线程消息队列中排队显示，不阻塞异常处理线程。
     */
    private fun showUserFriendlyError(throwable: Throwable) {
        appContext?.let { ctx ->
            try {
                val message = getUserFriendlyMessage(throwable)
                Handler(Looper.getMainLooper()).post {
                    try {
                        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to show crash Toast on main thread")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to post crash Toast to main thread")
            }
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
                appContext?.let {
                    try {
                        Toast.makeText(it, errorMessage, Toast.LENGTH_SHORT).show()
                    } catch (ex: Exception) {
                        Timber.w(ex, "Failed to show safeExecute Toast")
                    }
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
                appContext?.let {
                    try {
                        Toast.makeText(it, errorMessage, Toast.LENGTH_SHORT).show()
                    } catch (ex: Exception) {
                        Timber.w(ex, "Failed to show safeExecuteSuspend Toast")
                    }
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
