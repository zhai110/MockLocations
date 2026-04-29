package com.mockloc.util

import android.content.Context
import com.mockloc.util.PrefsConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 地址缓存管理器
 * 
 * 功能：
 * 1. 缓存经纬度到地址的映射关系
 * 2. 减少重复的地理编码请求
 * 3. 自动清理过期缓存
 * 4. 限制缓存大小，防止内存泄漏
 */
object AddressCache {
    
    private const val MAX_CACHE_SIZE = 100  // 最大缓存条目数
    private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L  // 24小时过期
    private const val SAVE_DELAY_MS = 500L  // 保存延迟（防抖）
    
    private var cache: MutableMap<String, CacheEntry> = ConcurrentHashMap()
    // 强制使用 applicationContext，防止 Activity Context 导致内存泄漏
    private var appContext: Context? = null
    
    // ✅ 延迟保存相关
    private var saveJob: Job? = null
    private val saveScope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 缓存条目
     */
    data class CacheEntry(
        val address: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 初始化缓存
     */
    fun init(appContext: Context) {
        this.appContext = appContext.applicationContext
        loadCacheFromPrefs()
        Timber.d("AddressCache initialized with ${cache.size} entries")
    }
    
    /**
     * 从 SharedPreferences 加载缓存
     */
    private fun loadCacheFromPrefs() {
        try {
            val prefs = appContext?.getSharedPreferences(PrefsConfig.ADDRESS_CACHE, Context.MODE_PRIVATE)
            val allEntries = prefs?.all
            
            if (allEntries.isNullOrEmpty()) {
                cache.clear()
                return
            }
            
            cache.clear()
            for ((key, value) in allEntries) {
                if (value is String) {
                    val parts = value.split("|", limit = 2)
                    if (parts.size == 2) {
                        val address = parts[0]
                        val timestamp = parts[1].toLongOrNull() ?: 0L
                        
                        if (System.currentTimeMillis() - timestamp < CACHE_EXPIRY_MS) {
                            cache[key] = CacheEntry(address, timestamp)
                        }
                    }
                }
            }
            
            if (cache.size > MAX_CACHE_SIZE) {
                cleanupOldEntries()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load address cache")
            cache.clear()
        }
    }
    
    /**
     * 保存缓存到 SharedPreferences（带防抖）
     */
    private fun saveCacheToPrefs() {
        // 取消之前的保存任务
        saveJob?.cancel()
        
        // 延迟保存，合并短时间内的多次修改
        saveJob = saveScope.launch {
            delay(SAVE_DELAY_MS)
            performSave()
        }
    }
    
    /**
     * 执行实际的保存操作
     */
    private fun performSave() {
        try {
            val prefs = appContext?.getSharedPreferences(PrefsConfig.ADDRESS_CACHE, Context.MODE_PRIVATE)
            val editor = prefs?.edit()
            
            // 清除旧数据
            editor?.clear()
            
            // 保存新数据
            cache.forEach { (key, entry) ->
                editor?.putString(key, "${entry.address}|${entry.timestamp}")
            }
            
            editor?.apply()
            Timber.d("Batch saved ${cache.size} cache entries")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save address cache")
        }
    }
    
    /**
     * 获取缓存的地址
     * 
     * @param lat 纬度
     * @param lng 经度
     * @return 缓存的地址，如果不存在则返回 null
     */
    fun getAddress(lat: Double, lng: Double): String? {
        val key = "${lat},${lng}"
        val entry = cache[key]
        
        return if (entry != null && !isExpired(entry)) {
            Timber.d("Cache hit for: $key")
            entry.address
        } else {
            if (entry != null) {
                cache.remove(key)
                saveCacheToPrefs()
            }
            null
        }
    }
    
    /**
     * 缓存地址
     * 
     * @param lat 纬度
     * @param lng 经度
     * @param address 地址字符串
     */
    fun putAddress(lat: Double, lng: Double, address: String) {
        val key = "${lat},${lng}"
        
        if (cache.size >= MAX_CACHE_SIZE) {
            cleanupOldEntries()
        }
        
        cache[key] = CacheEntry(address)
        saveCacheToPrefs()
        Timber.d("Cached address for: $key")
    }
    
    /**
     * 检查缓存条目是否过期
     */
    private fun isExpired(entry: CacheEntry): Boolean {
        return System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRY_MS
    }
    
    /**
     * 清理过期的条目
     */
    private fun cleanupOldEntries() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = cache.filterValues { 
            currentTime - it.timestamp > CACHE_EXPIRY_MS 
        }.keys
        
        expiredKeys.forEach { cache.remove(it) }
        
        if (cache.size > MAX_CACHE_SIZE) {
            val sortedEntries = cache.entries.sortedBy { it.value.timestamp }
            val toRemove = sortedEntries.take(sortedEntries.size - MAX_CACHE_SIZE)
            toRemove.forEach { cache.remove(it.key) }
        }
        
        saveCacheToPrefs()
        Timber.d("Cleaned up cache, remaining: ${cache.size}")
    }
    
    /**
     * 清空所有缓存
     */
    fun clear() {
        cache.clear()
        saveCacheToPrefs()
        Timber.d("Address cache cleared")
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "size" to cache.size,
            "maxSize" to MAX_CACHE_SIZE,
            "expiryHours" to CACHE_EXPIRY_MS / (60 * 60 * 1000)
        )
    }
    
    /**
     * 释放资源，取消协程作用域
     */
    fun destroy() {
        saveJob?.cancel()
        saveScope.cancel()
        Timber.d("AddressCache destroyed")
    }
}
