package com.mockloc.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.mockloc.R
import com.mockloc.ui.update.UpdateDialogFragment
import com.mockloc.util.UpdateChecker
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 主界面Activity（轻量级容器）
 *
 * 职责：
 * - 托管 MainFragment
 * - 通过 ServiceConnector 管理 LocationService 生命周期
 * - 提供 ViewModel 实例（通过 ViewModelProvider）
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

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
    }

    override fun onStart() {
        super.onStart()
        // ✅ 通过 ServiceConnector 绑定 Service（内部有防重复绑定逻辑）
        viewModel.bindService()
        Timber.d("MainActivity: triggered service bind via ServiceConnector")
    }

    override fun onStop() {
        super.onStop()
        // ✅ 不在这里解绑 Service，保持连接以支持后台悬浮窗控制
        // Service 将在 ViewModel.onCleared() 或应用退出时解绑
    }

    override fun onDestroy() {
        super.onDestroy()

        // ✅ Activity 销毁时解绑 Service（通过 ServiceConnector）
        viewModel.unbindService()
        Timber.d("MainActivity: triggered service unbind via ServiceConnector")
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
                        Timber.d("发现新版本: ${updateInfo.versionName}")
                        UpdateDialogFragment.newInstance(updateInfo)
                            .show(supportFragmentManager, "update_dialog")
                    } else {
                        Timber.d("当前已是最新版本")
                    }
                }.onFailure { error ->
                    Timber.w(error, "检查更新失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "检查更新异常")
            }
        }
    }
}
