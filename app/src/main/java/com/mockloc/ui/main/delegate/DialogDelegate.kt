package com.mockloc.ui.main.delegate

import android.widget.EditText
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import com.amap.api.maps.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mockloc.R
import com.mockloc.util.MapUtils
import com.mockloc.util.UIFeedbackHelper
import timber.log.Timber

/**
 * 对话框委托类
 *
 * 职责：
 * - 显示手动输入坐标对话框（支持 WGS-84 / BD-09 / GCJ-02 输入）
 * - 其他需要从 MainFragment 迁移的对话框
 *
 * 设计说明：
 * - 对话框不持有状态，每次调用时创建新实例
 * - 坐标转换结果通过回调返回给调用方
 */
class DialogDelegate(
    private val fragment: Fragment
) {
    /**
     * 显示手动输入坐标对话框
     *
     * 支持三种坐标系输入：
     * - GCJ-02（高德地图坐标系，默认）
     * - WGS-84（GPS 原始坐标系，自动转换为 GCJ-02）
     * - BD-09（百度坐标系，自动转换为 GCJ-02）
     *
     * @param onCoordinateSelected 坐标选择回调，参数为转换后的 GCJ-02 坐标
     */
    fun showInputCoordsDialog(onCoordinateSelected: (LatLng) -> Unit) {
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_input_coords, null)
        val latEdit = dialogView.findViewById<EditText>(R.id.edit_latitude)
        val lngEdit = dialogView.findViewById<EditText>(R.id.edit_longitude)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radio_group_coordinate_system)

        MaterialAlertDialogBuilder(fragment.requireContext())
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                try {
                    val lat = latEdit.text.toString().toDouble()
                    val lng = lngEdit.text.toString().toDouble()

                    if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                        UIFeedbackHelper.showToast(fragment.requireContext(), "坐标超出有效范围")
                        return@setPositiveButton
                    }

                    val selectedCoordType = when (radioGroup.checkedRadioButtonId) {
                        R.id.radio_gcj02 -> "GCJ02"
                        R.id.radio_wgs84 -> "WGS84"
                        R.id.radio_bd09 -> "BD09"
                        else -> "GCJ02"
                    }

                    val gcjLatLng = when (selectedCoordType) {
                        "GCJ02" -> LatLng(lat, lng)
                        "WGS84" -> {
                            val gcj = MapUtils.wgs84ToGcj02(lng, lat)
                            LatLng(gcj[1], gcj[0])
                        }
                        "BD09" -> {
                            val gcj = MapUtils.bd09ToGcj02(lng, lat)
                            LatLng(gcj[1], gcj[0])
                        }
                        else -> LatLng(lat, lng)
                    }

                    onCoordinateSelected(gcjLatLng)
                    Timber.d("Input coords: $lat, $lng ($selectedCoordType) -> GCJ02: ${gcjLatLng.latitude}, ${gcjLatLng.longitude}")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to parse coordinates")
                    UIFeedbackHelper.showToast(fragment.requireContext(), "坐标格式错误")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
