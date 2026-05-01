package com.mockloc.repository

import android.content.Context
import com.amap.api.services.core.AMapException
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch
import com.amap.api.maps.model.LatLng
import timber.log.Timber

class PoiSearchHelper(private val context: Context) {

    // 关键字搜索（支持周边搜索）
    fun searchPlace(
        keyword: String,
        callback: (List<PlaceItem>) -> Unit,
        centerLat: Double? = null,
        centerLng: Double? = null,
        radius: Int = 20000  // ✅ 默认搜索半径改为20km
    ) {
        Timber.d("搜索地点: keyword=$keyword, center=[$centerLat,$centerLng], radius=$radius")
        performSearch(keyword, callback, centerLat, centerLng, radius, attempt = 1)
    }
    
    /**
     * 执行搜索（支持自动扩大范围）
     * @param attempt 当前尝试次数（1=首次20km, 2=扩大至50km）
     */
    private fun performSearch(
        keyword: String,
        callback: (List<PlaceItem>) -> Unit,
        centerLat: Double?,
        centerLng: Double?,
        radius: Int,
        attempt: Int
    ) {
        val query = PoiSearch.Query(keyword, "", "全国")
        query.pageSize = 20
        query.pageNum = 0
        val poiSearch = PoiSearch(context, query)

        // 如果有中心点，设置为周边搜索（按距离排序）
        if (centerLat != null && centerLng != null) {
            val center = LatLonPoint(centerLat, centerLng)
            val bound = PoiSearch.SearchBound(center, radius, true)
            poiSearch.bound = bound
        }

        poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
            override fun onPoiSearched(result: PoiResult?, rCode: Int) {
                if (rCode == AMapException.CODE_AMAP_SUCCESS && result != null) {
                    val list = result.pois.map {
                        PlaceItem(
                            name = it.title,
                            address = it.snippet,
                            lat = it.latLonPoint.latitude,
                            lng = it.latLonPoint.longitude,
                            distance = it.distance
                        )
                    }
                    
                    // ✅ 方案D：如果结果少于3条且是首次搜索，自动扩大到50km重新搜索
                    if (list.size < 3 && attempt == 1 && centerLat != null && centerLng != null) {
                        Timber.d("搜索结果较少(${list.size}条)，自动扩大搜索范围至50km")
                        performSearch(keyword, callback, centerLat, centerLng, 50000, attempt = 2)
                        return
                    }
                    
                    Timber.d("搜索成功，找到${list.size}个结果 (attempt=$attempt, radius=${radius/1000}km)")
                    callback(list)
                } else {
                    val errorMsg = getErrorMessage(rCode)
                    Timber.e("搜索失败，错误码: $rCode, 错误信息: $errorMsg")
                    callback(emptyList())
                }
            }

            override fun onPoiItemSearched(poiItem: com.amap.api.services.core.PoiItem?, rCode: Int) {
                // 空实现
            }
        })
        poiSearch.searchPOIAsyn()
    }

    // 坐标 -> 地址
    fun latLngToAddress(lat: Double, lng: Double, callback: (name: String, fullAddress: String) -> Unit) {
        Timber.d("逆地理编码: lat=$lat, lng=$lng")

        // 优先查缓存，命中则跳过网络请求
        val cached = com.mockloc.util.AddressCache.getAddress(lat, lng)
        if (cached != null) {
            val name = extractShortName(cached)
            Timber.d("逆地理编码缓存命中: name=$name, full=$cached")
            callback(name, cached)
            return
        }

        val geocoderSearch = GeocodeSearch(context)
        val point = LatLonPoint(lat, lng)
        val query = RegeocodeQuery(point, 200f, GeocodeSearch.AMAP)

        geocoderSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                if (rCode == AMapException.CODE_AMAP_SUCCESS && result != null) {
                    val fullAddress = result.regeocodeAddress.formatAddress
                    val name = extractShortName(fullAddress)
                    Timber.d("逆地理编码成功: name=$name, full=$fullAddress")
                    // 写入缓存
                    com.mockloc.util.AddressCache.putAddress(lat, lng, fullAddress)
                    callback(name, fullAddress)
                } else {
                    val errorMsg = getErrorMessage(rCode)
                    Timber.e("逆地理编码失败，错误码: $rCode, 错误信息: $errorMsg")
                    callback("", "")
                }
            }

            override fun onGeocodeSearched(p0: GeocodeResult?, p1: Int) {}
        })
        geocoderSearch.getFromLocationAsyn(query)
    }

    /**
     * 从逆地理编码结果中提取最短有意义名称
     * 优先级：POI名称 > 结构化街道+门牌 > formatAddress最后两段 > 完整地址
     */
    private fun extractShortName(fullAddress: String): String {
        if (fullAddress.isEmpty()) return fullAddress

        // 去掉省+市前缀
        val trimmed = fullAddress
            .replaceFirst(Regex("^[^省]+省"), "")
            .replaceFirst(Regex("^[^市]+市"), "")
            .trim()

        // 如果去掉省市区后仍较长，尝试只保留区以后的部分
        val afterDistrict = trimmed.replaceFirst(Regex("^[^区]+区"), "").trim()
        return if (afterDistrict.length > 2 && afterDistrict.length < trimmed.length) {
            afterDistrict
        } else if (trimmed.length > 2) {
            trimmed
        } else {
            fullAddress
        }
    }

    // 地址 -> 坐标
    fun addressToLatLng(address: String, callback: (LatLng?) -> Unit) {
        Timber.d("正地理编码: address=$address")
        val geocoderSearch = GeocodeSearch(context)
        val query = GeocodeQuery(address, "")

        geocoderSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {
                if (rCode == AMapException.CODE_AMAP_SUCCESS && result != null) {
                    result.geocodeAddressList.firstOrNull()?.let {
                        val latLng = LatLng(it.latLonPoint.latitude, it.latLonPoint.longitude)
                        Timber.d("正地理编码成功: ${latLng.latitude}, ${latLng.longitude}")
                        callback(latLng)
                    } ?: callback(null)
                } else {
                    val errorMsg = getErrorMessage(rCode)
                    Timber.e("正地理编码失败，错误码: $rCode, 错误信息: $errorMsg")
                    callback(null)
                }
            }

            override fun onRegeocodeSearched(p0: RegeocodeResult?, p1: Int) {}
        })
        geocoderSearch.getFromLocationNameAsyn(query)
    }

    data class PlaceItem(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val distance: Int = -1
    )
    
    /**
     * 根据错误码获取友好的错误消息
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            AMapException.CODE_AMAP_INVALID_USER_KEY -> "API Key无效，请联系开发者"
            AMapException.CODE_AMAP_ACCESS_TOO_FREQUENT -> "访问过于频繁，请稍后再试"
            1001 -> "服务错误，请稍后重试"
            1002 -> "API Key无效或过期"
            1003 -> "服务不可用"
            1004 -> "请求过于频繁"
            1005 -> "网络连接失败"
            1006 -> "超时，请检查网络"
            else -> "请求失败 (错误码: $errorCode)"
        }
    }
}
