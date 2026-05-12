package com.mockloc.core.common

/**
 * 统一结果封装
 *
 * 避免使用 kotlin.Result（与 Kotlin 序列化/内联类有冲突），
 * 自定义 sealed class 实现同样的效果，且支持扩展。
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: Exception, val message: String? = null) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }

    fun exceptionOrNull(): Exception? = when (this) {
        is Error -> exception
        is Success -> null
    }
}

/**
 * 安全调用包装：将可能抛出异常的代码块包装为 AppResult
 */
inline fun <T> safeCall(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: Exception) {
    AppResult.Error(e)
}

/**
 * 成功时执行操作
 */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

/**
 * 失败时执行操作
 */
inline fun <T> AppResult<T>.onError(action: (Exception) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(exception)
    return this
}

/**
 * 映射成功值
 */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Error -> this
}
