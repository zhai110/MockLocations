package com.mockloc.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * 网络和定位状态检查工具
 * 
 * 功能：
 * 1. 检查网络连接状态
 * 2. 检查定位服务是否可用
 * 3. 检查定位权限
 * 4. 提供友好的错误提示
 */
object NetworkLocationHelper {
    
    /**
     * 网络状态
     */
    enum class NetworkStatus {
        CONNECTED,      // 已连接
        DISCONNECTED,   // 未连接
        METERED,        // 按流量计费（移动数据）
        UNMETERED       // 非按流量计费（WiFi）
    }
    
    /**
     * 定位状态
     */
    enum class LocationStatus {
        AVAILABLE,          // 可用
        PERMISSION_DENIED,  // 权限被拒绝
        SERVICE_DISABLED,   // 服务被禁用
        PROVIDER_UNAVAILABLE // 提供者不可用
    }
    
    /**
     * 检查网络连接状态
     */
    fun checkNetworkStatus(context: Context): NetworkStatus {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            when {
                capabilities == null -> NetworkStatus.DISCONNECTED
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkStatus.UNMETERED
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkStatus.METERED
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkStatus.UNMETERED
                else -> NetworkStatus.DISCONNECTED
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            when {
                networkInfo == null || !networkInfo.isConnected -> NetworkStatus.DISCONNECTED
                networkInfo.type == ConnectivityManager.TYPE_WIFI -> NetworkStatus.UNMETERED
                networkInfo.type == ConnectivityManager.TYPE_MOBILE -> NetworkStatus.METERED
                else -> NetworkStatus.DISCONNECTED
            }
        }
    }
    
    /**
     * 检查是否有网络连接
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return checkNetworkStatus(context) != NetworkStatus.DISCONNECTED
    }
    
    /**
     * 检查是否在 WiFi 环境
     */
    fun isWifiConnected(context: Context): Boolean {
        return checkNetworkStatus(context) == NetworkStatus.UNMETERED
    }
    
    /**
     * 检查定位权限
     */
    fun checkLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        return fineLocation == PackageManager.PERMISSION_GRANTED ||
               coarseLocation == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查定位服务是否启用
     */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
               locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    
    /**
     * 检查定位状态
     */
    fun checkLocationStatus(context: Context): LocationStatus {
        // 先检查权限
        if (!checkLocationPermission(context)) {
            return LocationStatus.PERMISSION_DENIED
        }
        
        // 再检查服务
        if (!isLocationServiceEnabled(context)) {
            return LocationStatus.SERVICE_DISABLED
        }
        
        // 最后检查提供者
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasProvider = locationManager.allProviders.any { provider ->
            try {
                locationManager.isProviderEnabled(provider)
            } catch (e: Exception) {
                false
            }
        }
        
        return if (hasProvider) {
            LocationStatus.AVAILABLE
        } else {
            LocationStatus.PROVIDER_UNAVAILABLE
        }
    }
    
    /**
     * 获取网络状态的友好描述
     */
    fun getNetworkStatusMessage(status: NetworkStatus): String {
        return when (status) {
            NetworkStatus.CONNECTED -> "网络已连接"
            NetworkStatus.DISCONNECTED -> "网络未连接，请检查网络设置"
            NetworkStatus.METERED -> "当前使用移动数据，可能产生流量费用"
            NetworkStatus.UNMETERED -> "已连接到 WiFi"
        }
    }
    
    /**
     * 获取定位状态的友好描述
     */
    fun getLocationStatusMessage(status: LocationStatus): String {
        return when (status) {
            LocationStatus.AVAILABLE -> "定位服务正常"
            LocationStatus.PERMISSION_DENIED -> "定位权限被拒绝，请在设置中授予权限"
            LocationStatus.SERVICE_DISABLED -> "定位服务未开启，请在设置中开启"
            LocationStatus.PROVIDER_UNAVAILABLE -> "定位提供者不可用"
        }
    }
    
    /**
     * 综合检查（网络 + 定位）
     * 
     * @return Pair<是否全部正常, 错误消息列表>
     */
    fun comprehensiveCheck(context: Context): Pair<Boolean, List<String>> {
        val issues = mutableListOf<String>()
        
        // 检查网络
        val networkStatus = checkNetworkStatus(context)
        if (networkStatus == NetworkStatus.DISCONNECTED) {
            issues.add(getNetworkStatusMessage(networkStatus))
        }
        
        // 检查定位
        val locationStatus = checkLocationStatus(context)
        if (locationStatus != LocationStatus.AVAILABLE) {
            issues.add(getLocationStatusMessage(locationStatus))
        }
        
        return Pair(issues.isEmpty(), issues)
    }
    
    /**
     * 记录诊断信息（用于调试）
     */
    fun logDiagnostics(context: Context) {
        val networkStatus = checkNetworkStatus(context)
        val locationStatus = checkLocationStatus(context)
        
        Timber.d("=== 诊断信息 ===")
        Timber.d("网络状态: ${getNetworkStatusMessage(networkStatus)}")
        Timber.d("定位状态: ${getLocationStatusMessage(locationStatus)}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            capabilities?.let {
                Timber.d("网络能力:")
                Timber.d("  - WiFi: ${it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
                Timber.d("  - 移动数据: ${it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
                Timber.d("  - 以太网: ${it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}")
                Timber.d("  - VPN: ${it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)}")
            }
        }
        
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        Timber.d("定位提供者:")
        locationManager.allProviders.forEach { provider ->
            val enabled = try {
                locationManager.isProviderEnabled(provider)
            } catch (e: Exception) {
                false
            }
            Timber.d("  - $provider: ${if (enabled) "启用" else "禁用"}")
        }
    }
}
