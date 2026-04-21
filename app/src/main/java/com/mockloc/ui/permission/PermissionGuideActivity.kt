package com.mockloc.ui.permission

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mockloc.R
import com.mockloc.databinding.ActivityPermissionGuideBinding
import com.mockloc.util.PermissionHelper
import timber.log.Timber

/**
 * 权限引导页面
 */
class PermissionGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionGuideBinding

    // 替代废弃的 onActivityResult：从系统设置页返回时刷新权限状态
    private val settingsResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    // 替代废弃的 onRequestPermissionsResult：权限请求结果回调
    private val permissionLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupClickListeners()
        updatePermissionStatus()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupClickListeners() {
        // 定位权限
        binding.itemLocation.setOnClickListener {
            PermissionHelper.requestLocationPermissions(this)
        }

        // 后台定位权限
        binding.itemBackgroundLocation.setOnClickListener {
            openAppSettings()
        }

        // 悬浮窗权限
        binding.itemOverlay.setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }

        // 模拟定位权限
        binding.itemMockLocation.setOnClickListener {
            openDeveloperSettings()
        }

        // 完成按钮
        binding.btnComplete.setOnClickListener {
            if (checkAllPermissions()) {
                finish()
            } else {
                showMissingPermissionsMessage()
            }
        }
    }

    private fun updatePermissionStatus() {
        try {
            // 定位权限状态
            val hasLocation = PermissionHelper.hasLocationPermissions(this)
            binding.statusLocation.text = if (hasLocation) "已授权" else "未授权"
            binding.statusLocation.setTextColor(
                getColor(if (hasLocation) com.mockloc.R.color.success else com.mockloc.R.color.error)
            )

            // 后台定位权限状态
            val hasBackground = PermissionHelper.hasBackgroundLocationPermission(this)
            binding.statusBackgroundLocation.text = if (hasBackground) "已授权" else "未授权"
            binding.statusBackgroundLocation.setTextColor(
                getColor(if (hasBackground) com.mockloc.R.color.success else com.mockloc.R.color.error)
            )

            // 悬浮窗权限状态
            val hasOverlay = PermissionHelper.hasOverlayPermission(this)
            binding.statusOverlay.text = if (hasOverlay) "已授权" else "未授权"
            binding.statusOverlay.setTextColor(
                getColor(if (hasOverlay) com.mockloc.R.color.success else com.mockloc.R.color.error)
            )

            // 模拟定位权限状态
            val hasMock = PermissionHelper.hasMockLocationPermission(this)
            binding.statusMockLocation.text = if (hasMock) "已授权" else "未授权"
            binding.statusMockLocation.setTextColor(
                getColor(if (hasMock) com.mockloc.R.color.success else com.mockloc.R.color.error)
            )

            // 更新完成按钮状态
            binding.btnComplete.isEnabled = checkAllPermissions()
        } catch (e: Exception) {
            Timber.e(e, "updatePermissionStatus failed")
        }
    }

    private fun checkAllPermissions(): Boolean {
        return PermissionHelper.hasLocationPermissions(this) &&
               PermissionHelper.hasBackgroundLocationPermission(this) &&
               PermissionHelper.hasOverlayPermission(this) &&
               PermissionHelper.hasMockLocationPermission(this)
    }

    private fun showMissingPermissionsMessage() {
        val missing = mutableListOf<String>()
        if (!PermissionHelper.hasLocationPermissions(this)) missing.add("定位权限")
        if (!PermissionHelper.hasBackgroundLocationPermission(this)) missing.add("后台定位权限")
        if (!PermissionHelper.hasOverlayPermission(this)) missing.add("悬浮窗权限")
        if (!PermissionHelper.hasMockLocationPermission(this)) missing.add("模拟定位权限")

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("权限缺失")
            .setMessage("请先授予以下权限：\n\n${missing.joinToString("\n") { "• $it" }}")
            .setPositiveButton("确定", null)
            .create()
        
        // 应用圆角背景
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
        }
        
        dialog.show()
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        settingsResultLauncher.launch(intent)
    }

    private fun openDeveloperSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        try {
            settingsResultLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open developer settings")
            // 如果无法打开开发者设置，提示用户手动操作
            val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("请手动打开开发者选项，并在\"选择模拟位置信息应用\"中选择本应用")
                .setPositiveButton("确定", null)
                .create()
            
            // 应用圆角背景
            dialog.setOnShowListener {
                dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_rounded)
            }
            
            dialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
}
