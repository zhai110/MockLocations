package com.mockloc.util

/**
 * 统一的结果封装类
 * 用于处理异步操作的成功、失败和加载状态
 */
sealed class Result<out T> {
    /**
     * 成功状态
     */
    data class Success<T>(val data: T) : Result<T>()
    
    /**
     * 错误状态
     */
    data class Error(
        val exception: Throwable,
        val message: String? = exception.message
    ) : Result<Nothing>()
    
    /**
     * 加载状态
     */
    object Loading : Result<Nothing>()
    
    /**
     * 判断是否成功
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * 判断是否失败
     */
    val isError: Boolean
        get() = this is Error
    
    /**
     * 判断是否加载中
     */
    val isLoading: Boolean
        get() = this is Loading
    
    /**
     * 获取成功数据，失败时返回null
     */
    fun getOrNull(): T? {
        return (this as? Success)?.data
    }
    
    /**
     * 获取错误信息
     */
    fun getErrorMessage(): String? {
        return (this as? Error)?.message
    }
}

/**
 * 安全地执行 suspend 函数，捕获异常并返回 Result
 */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(e)
    }
}

/**
 * 安全地执行普通函数，捕获异常并返回 Result
 */
fun <T> safeCallSync(block: () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: Exception) {
        Result.Error(e)
    }
}

/**
 * 对 Result 进行映射转换
 */
fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> {
    return when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> this
        Result.Loading -> Result.Loading
    }
}

/**
 * 对 Result 进行折叠处理
 */
fun <T, R> Result<T>.fold(
    onSuccess: (T) -> R,
    onError: (Throwable) -> R,
    onLoading: () -> R
): R {
    return when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> onError(exception)
        Result.Loading -> onLoading()
    }
}

/**
 * 如果成功则执行动作
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

/**
 * 如果失败则执行动作
 */
inline fun <T> Result<T>.onError(action: (Throwable) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(exception)
    }
    return this
}

/**
 * 如果加载中则执行动作
 */
inline fun <T> Result<T>.onLoading(action: () -> Unit): Result<T> {
    if (this is Result.Loading) {
        action()
    }
    return this
}
