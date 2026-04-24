package com.mockloc.util

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * 更新信息数据模型
 * 
 * 用于从远程服务器获取版本更新信息
 */
data class UpdateInfo(
    @SerializedName("versionCode")
    val versionCode: Int,
    
    @SerializedName("versionName")
    val versionName: String,
    
    @SerializedName("downloadUrl")
    val downloadUrl: String,
    
    @SerializedName("releaseNotes")
    val releaseNotes: String,
    
    @SerializedName("forceUpdate")
    val forceUpdate: Boolean = false,
    
    @SerializedName("minVersionCode")
    val minVersionCode: Int = 1,
    
    @SerializedName("fileSize")
    val fileSize: Long = 0L,
    
    @SerializedName("md5")
    val md5: String = "",
    
    @SerializedName("publishTime")
    val publishTime: String = ""
) : Serializable {
    
    /**
     * 判断是否有新版本
     */
    fun hasUpdate(currentVersionCode: Int): Boolean {
        return versionCode > currentVersionCode
    }
    
    /**
     * 判断是否强制更新
     */
    fun isForceUpdate(currentVersionCode: Int): Boolean {
        return forceUpdate && versionCode > currentVersionCode
    }
    
    /**
     * 获取文件大小（格式化）
     */
    fun getFileSizeFormatted(): String {
        return when {
            fileSize >= 1024 * 1024 -> String.format("%.2f MB", fileSize / 1024.0 / 1024.0)
            fileSize >= 1024 -> String.format("%.2f KB", fileSize / 1024.0)
            else -> "$fileSize B"
        }
    }
    
    /**
     * 验证更新信息的完整性
     */
    fun isValid(): Boolean {
        return versionCode > 0 && 
               versionName.isNotEmpty() && 
               downloadUrl.isNotEmpty() &&
               releaseNotes.isNotEmpty()
    }
}
