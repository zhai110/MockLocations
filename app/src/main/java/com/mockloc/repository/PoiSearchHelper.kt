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
        radius: Int = 5000
    ) {
        Timber.d("搜索地点: keyword=$keyword, center=[$centerLat,$centerLng], radius=$radius")
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
                    Timber.d("搜索成功，找到${list.size}个结果")
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
    fun latLngToAddress(lat: Double, lng: Double, callback: (String) -> Unit) {
        Timber.d("逆地理编码: lat=$lat, lng=$lng")
        val geocoderSearch = GeocodeSearch(context)
        val point = LatLonPoint(lat, lng)
        val query = RegeocodeQuery(point, 200f, GeocodeSearch.AMAP)

        geocoderSearch.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
            override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                if (rCode == AMapException.CODE_AMAP_SUCCESS && result != null) {
                    val address = result.regeocodeAddress.formatAddress
                    Timber.d("逆地理编码成功: $address")
                    callback(address)
                } else {
                    val errorMsg = getErrorMessage(rCode)
                    Timber.e("逆地理编码失败，错误码: $rCode, 错误信息: $errorMsg")
                    callback("获取地址失败")
                }
            }

            override fun onGeocodeSearched(p0: GeocodeResult?, p1: Int) {}
        })
        geocoderSearch.getFromLocationAsyn(query)
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
