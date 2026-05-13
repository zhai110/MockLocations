package com.mockloc.util

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 远程更新检查器
 * 
 * 功能：
 * 1. 从 GitHub Releases 获取最新版本信息
 * 2. 对比当前版本，判断是否需要更新
 * 3. 下载 APK 文件
 * 4. 触发安装
 */
class UpdateChecker(private val context: Context) {
    
    companion object {
        // 远程更新配置文件地址
        // 使用 Gitee（国内访问速度快）
        private const val UPDATE_JSON_URL = 
            "https://gitee.com/eizmme/MockLocations/raw/master/update.json"
        
        // 备选方案：GitHub + 代理
        // private const val UPDATE_JSON_URL = 
        //     "https://ghproxy.com/https://raw.githubusercontent.com/zhai110/MockLocations/master/update.json"
        
        // 下载超时时间（秒）
        private const val DOWNLOAD_TIMEOUT = 300L
        
        // APK 保存目录
        private const val APK_DIR_NAME = "updates"
        
        // ✅ 更新检查频率控制：1 小时（毫秒）
        private const val CHECK_INTERVAL = 60 * 60 * 1000L
        
        // SharedPreferences 键名
        private const val PREF_NAME = "update_checker"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_LAST_CHECK_RESULT = "last_check_result"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * 获取当前应用版本信息
     * 
     * @return Pair<versionCode, versionName>
     */
    fun getCurrentVersionInfo(): Pair<Long, String> {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            Pair(packageInfo.longVersionCode, packageInfo.versionName ?: "1.0.0")
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e, "Failed to get package info")
            Pair(1L, "1.0.0")
        }
    }
    
    /**
     * 检查更新（带频率控制）
     * 
     * @param forceCheck 是否强制检查（忽略频率限制）
     * @return Result<UpdateInfo?> 如果有新版本返回更新信息，否则返回 null
     */
    suspend fun checkForUpdate(forceCheck: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        try {
            // ✅ 频率控制：检查是否在冷却时间内
            if (!forceCheck) {
                val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
                val currentTime = System.currentTimeMillis()
                val timeSinceLastCheck = currentTime - lastCheckTime
                
                if (timeSinceLastCheck < CHECK_INTERVAL) {
                    val remainingMinutes = (CHECK_INTERVAL - timeSinceLastCheck) / 1000 / 60
                    Timber.d("⏭️ Skip update check (last checked ${remainingMinutes} minutes ago)")
                    
                    // ✅ 修复：返回缓存结果前，验证当前版本是否已更新
                    val cachedResult = prefs.getString(KEY_LAST_CHECK_RESULT, null)
                    if (cachedResult != null) {
                        try {
                            val cachedUpdateInfo = gson.fromJson(cachedResult, UpdateInfo::class.java)
                            val (currentVersionCode, _) = getCurrentVersionInfo()
                            
                            // 如果当前版本 >= 缓存中的最新版本，说明已升级，清除缓存
                            if (currentVersionCode >= cachedUpdateInfo.versionCode) {
                                Timber.d("✅ Version upgraded, clearing cached update info")
                                prefs.edit().remove(KEY_LAST_CHECK_RESULT).apply()
                                return@withContext Result.success(null)
                            }
                            
                            // 否则返回缓存结果
                            return@withContext Result.success(cachedUpdateInfo)
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to parse cached update info")
                            prefs.edit().remove(KEY_LAST_CHECK_RESULT).apply()
                        }
                    }
                    return@withContext Result.success(null)
                }
            }
            
            Timber.d("🔄 Checking for updates from: $UPDATE_JSON_URL")
            
            // 1. 获取远程更新信息
            val request = Request.Builder()
                .url(UPDATE_JSON_URL)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Timber.w("Update check failed: HTTP ${response.code}")
                return@withContext Result.failure(Exception("检查更新失败：HTTP ${response.code}"))
            }
            
            val json = response.body?.string()
            if (json.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("更新信息为空"))
            }
            
            Timber.d("Update info received: $json")
            
            // 2. 解析更新信息
            val updateInfo = gson.fromJson(json, UpdateInfo::class.java)
            
            // 3. 验证数据完整性
            if (!updateInfo.isValid()) {
                return@withContext Result.failure(Exception("更新信息不完整"))
            }
            
            // 4. 获取当前版本
            val (currentVersionCode, currentVersionName) = getCurrentVersionInfo()
            Timber.d("Current version: $currentVersionName ($currentVersionCode)")
            Timber.d("Remote version: ${updateInfo.versionName} (${updateInfo.versionCode})")
            
            // 5. 判断是否有新版本
            val result = if (updateInfo.hasUpdate(currentVersionCode)) {
                Timber.i("✅ New version available: ${updateInfo.versionName} (${updateInfo.versionCode})")
                Result.success(updateInfo)
            } else {
                Timber.d("✓ Already up to date")
                Result.success(null)
            }
            
            // ✅ 保存检查结果和时间戳
            prefs.edit().apply {
                putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                // 如果有更新，缓存更新信息
                if (result.isSuccess && result.getOrNull() != null) {
                    putString(KEY_LAST_CHECK_RESULT, gson.toJson(result.getOrNull()))
                } else {
                    remove(KEY_LAST_CHECK_RESULT)
                }
                apply()
            }
            
            result
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to check for updates")
            Result.failure(e)
        }
    }
    
    /**
     * 下载 APK 文件
     * 
     * @param updateInfo 更新信息
     * @param progressCallback 下载进度回调 (0-100)
     * @return 下载的 APK 文件路径
     */
    suspend fun downloadApk(
        updateInfo: UpdateInfo,
        progressCallback: ((Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Downloading APK from: ${updateInfo.downloadUrl}")
            
            // 1. 创建下载目录
            val downloadDir = File(context.getExternalFilesDir(null), APK_DIR_NAME)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            // 2. 创建临时文件
            val apkFile = File(downloadDir, "MockLocations_v${updateInfo.versionName}.apk")
            
            // 3. 如果文件已存在，先删除
            if (apkFile.exists()) {
                apkFile.delete()
            }
            
            // 4. 发起下载请求
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("下载失败：HTTP ${response.code}"))
            }
            
            val body = response.body ?: return@withContext Result.failure(Exception("响应体为空"))
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            
            // 5. 写入文件
            FileOutputStream(apkFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // 计算进度
                        if (totalBytes > 0 && progressCallback != null) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            progressCallback(progress)
                        }
                    }
                }
            }
            
            // 6. 验证文件大小
            val actualSize = apkFile.length()
            Timber.i("APK downloaded successfully: ${apkFile.absolutePath}")
            Timber.d("Expected size: ${updateInfo.fileSize}, Actual size: $actualSize")
            
            Result.success(apkFile)
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to download APK")
            Result.failure(e)
        }
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload() {
        client.dispatcher.cancelAll()
        Timber.d("Download cancelled")
    }
}
