package com.mockloc.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.mockloc.R
import com.mockloc.service.LocationService
import com.mockloc.ui.update.UpdateDialogFragment
import com.mockloc.util.UpdateChecker
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 主界面Activity（轻量级容器）
 * 
 * 职责：
 * - 托管 MainFragment
 * - 管理 LocationService 生命周期（绑定/解绑）
 * - 提供 ViewModel 实例（通过 ViewModelProvider）
 * - 向 Fragment 传递 Service 引用
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // LocationService 绑定
    private var locationService: LocationService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationService.LocalBinder
            locationService = binder.getService()
            isServiceBound = true
            Timber.d("LocationService bound")
            
            // 将 Service 引用传递给 ViewModel
            viewModel.setLocationService(locationService)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            isServiceBound = false
            Timber.d("LocationService unbound")
            
            // 清除 ViewModel 中的 Service 引用
            viewModel.setLocationService(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // ✅ 自动检查更新（静默检查，有新版本才提示）
        checkForUpdate()
        
        // 只在首次创建时添加 Fragment
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.root_layout, MainFragment(), "main_fragment")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d("MainActivity configuration changed: isNight=${(newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES}")
        // 配置变化会自动传递给 Fragment 的 onConfigurationChanged
    }

    override fun onStart() {
        super.onStart()
        try {
            // 绑定 LocationService
            val intent = Intent(this, LocationService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            Timber.d("Binding LocationService")
        } catch (e: Exception) {
            Timber.e(e, "Failed to bind LocationService")
        }
    }

    override fun onStop() {
        super.onStop()
        // 解绑 LocationService
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
                viewModel.setLocationService(null)
            } catch (e: Exception) {
                Timber.w(e, "Error unbinding LocationService")
            }
            isServiceBound = false
        }
    }
    
    /**
     * ✅ 自动检查更新（静默检查，有新版本才提示）
     */
    private fun checkForUpdate() {
        lifecycleScope.launch {
            try {
                val updateChecker = UpdateChecker(this@MainActivity)
                val result = updateChecker.checkForUpdate()
                
                result.onSuccess { updateInfo ->
                    if (updateInfo != null) {
                        // 有新版本，显示更新对话框
                        Timber.d("发现新版本: ${updateInfo.versionName}")
                        UpdateDialogFragment.newInstance(updateInfo)
                            .show(supportFragmentManager, "update_dialog")
                    } else {
                        // 没有新版本，静默处理
                        Timber.d("当前已是最新版本")
                    }
                }.onFailure { error ->
                    // 检查失败，静默处理（不显示错误提示）
                    Timber.w(error, "检查更新失败")
                }
            } catch (e: Exception) {
                // 捕获所有异常，确保不影响应用启动
                Timber.e(e, "检查更新异常")
            }
        }
    }
}
