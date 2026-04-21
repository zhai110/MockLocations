package com.mockloc.util

import kotlin.math.*

/**
 * 坐标转换工具类
 * 支持WGS84、GCJ02、BD09三种坐标系互转
 * 
 * WGS84: 国际标准坐标系，GPS原始坐标
 * GCJ02: 国测局坐标系，高德、腾讯地图使用
 * BD09: 百度坐标系，百度地图使用
 */
object MapUtils {
    
    private const val PI = 3.14159265358979324
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val X_PI = PI * 3000.0 / 180.0
    
    /**
     * BD09 转 WGS84
     */
    fun bd09ToWgs84(lng: Double, lat: Double): DoubleArray {
        val gcj02 = bd09ToGcj02(lng, lat)
        return gcj02ToWgs84(gcj02[0], gcj02[1])
    }
    
    /**
     * WGS84 转 BD09
     */
    fun wgs84ToBd09(lng: Double, lat: Double): DoubleArray {
        val gcj02 = wgs84ToGcj02(lng, lat)
        return gcj02ToBd09(gcj02[0], gcj02[1])
    }
    
    /**
     * WGS84 转 GCJ02
     */
    fun wgs84ToGcj02(lng: Double, lat: Double): DoubleArray {
        if (outOfChina(lng, lat)) {
            return doubleArrayOf(lng, lat)
        }
        
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        
        return doubleArrayOf(lng + dLng, lat + dLat)
    }
    
    /**
     * GCJ02 转 WGS84
     */
    fun gcj02ToWgs84(lng: Double, lat: Double): DoubleArray {
        if (outOfChina(lng, lat)) {
            return doubleArrayOf(lng, lat)
        }
        
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        
        return doubleArrayOf(lng * 2 - (lng + dLng), lat * 2 - (lat + dLat))
    }
    
    /**
     * BD09 转 GCJ02
     */
    fun bd09ToGcj02(bdLng: Double, bdLat: Double): DoubleArray {
        val x = bdLng - 0.0065
        val y = bdLat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        
        return doubleArrayOf(z * cos(theta), z * sin(theta))
    }
    
    /**
     * GCJ02 转 BD09
     */
    fun gcj02ToBd09(lng: Double, lat: Double): DoubleArray {
        val z = sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = atan2(lat, lng) + 0.000003 * cos(lng * X_PI)
        
        return doubleArrayOf(z * cos(theta) + 0.0065, z * sin(theta) + 0.006)
    }
    
    /**
     * 判断是否在中国境内
     */
    private fun outOfChina(lng: Double, lat: Double): Boolean {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
    }
    
    /**
     * 转换纬度
     */
    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }
    
    /**
     * 转换经度
     */
    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
