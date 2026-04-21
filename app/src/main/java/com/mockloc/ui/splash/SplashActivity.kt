package com.mockloc.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.mockloc.R
import com.mockloc.databinding.ActivitySplashBinding
import com.mockloc.ui.main.MainActivity
import timber.log.Timber

/**
 * 启动页 - 现代时尚风格
 * 
 * 优化点：
 * 1. 首次启动显示完整动画（1.2秒），非首次立即跳转
 * 2. 使用 SharedPreferences 判断是否首次启动
 * 3. 添加生命周期管理，防止内存泄漏
 * 4. 使用弹性插值器使动画更自然
 * 5. 锁定竖屏方向避免重建
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    
    // 动画配置常量
    companion object {
        private const val LOGO_ANIMATION_DURATION = 500L      // Logo 动画时长
        private const val TITLE_ANIMATION_DELAY = 200L        // 标题延迟
        private const val TITLE_ANIMATION_DURATION = 400L     // 标题动画时长
        private const val SUBTITLE_ANIMATION_DELAY = 400L     // 副标题延迟
        private const val SUBTITLE_ANIMATION_DURATION = 400L  // 副标题动画时长
        private const val FIRST_LAUNCH_DELAY = 1200L          // 首次启动延迟
    }
    
    // 标记是否为首次启动
    private var isFirstLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 检查是否为首次启动
        checkFirstLaunch()
        
        Timber.d("SplashActivity created, isFirstLaunch=$isFirstLaunch")

        if (isFirstLaunch) {
            // 首次启动：显示完整动画
            startAnimations()
            scheduleNavigation()
        } else {
            // 非首次启动：立即跳转
            navigateToMain()
        }
    }
    
    /**
     * 检查是否为首次启动
     */
    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences(com.mockloc.util.PrefKeys.PREFS_NAME, MODE_PRIVATE)
        isFirstLaunch = prefs.getBoolean(com.mockloc.util.PrefKeys.KEY_FIRST_LAUNCH, true)
        
        // 标记为非首次启动
        if (isFirstLaunch) {
            prefs.edit().putBoolean(com.mockloc.util.PrefKeys.KEY_FIRST_LAUNCH, false).apply()
            Timber.d("First launch detected, marked as completed")
        } else {
            Timber.d("Not first launch")
        }
    }

    /**
     * 启动动画序列
     */
    private fun startAnimations() {
        animateLogo()
        animateTitle()
        animateSubtitle()
    }
    
    /**
     * Logo 淡入 + 缩放动画（使用弹性插值器）
     */
    private fun animateLogo() {
        binding.ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(LOGO_ANIMATION_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
    
    /**
     * 标题淡入动画
     */
    private fun animateTitle() {
        binding.tvAppName.postDelayed({
            binding.tvAppName.animate()
                .alpha(1f)
                .setDuration(TITLE_ANIMATION_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }, TITLE_ANIMATION_DELAY)
    }
    
    /**
     * 副标题淡入动画
     */
    private fun animateSubtitle() {
        binding.tvSubtitle.postDelayed({
            binding.tvSubtitle.animate()
                .alpha(1f)
                .setDuration(SUBTITLE_ANIMATION_DURATION)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }, SUBTITLE_ANIMATION_DELAY)
    }
    
    /**
     * 调度页面跳转（仅首次启动使用）
     */
    private fun scheduleNavigation() {
        binding.root.postDelayed({
            Timber.d("Splash animation completed, navigating to main")
            navigateToMain()
        }, FIRST_LAUNCH_DELAY)
    }

    /**
     * 跳转到主界面
     */
    private fun navigateToMain() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start MainActivity")
            // 如果跳转失败，显示错误提示并退出
            android.widget.Toast.makeText(
                this, 
                getString(R.string.splash_error_start_failed), 
                android.widget.Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
    
    /**
     * 清理资源，防止内存泄漏
     */
    override fun onDestroy() {
        super.onDestroy()
        // 移除所有待执行的回调
        binding.root.removeCallbacks(null)
        // 取消所有正在进行的动画并重置状态
        resetAnimationState()
        Timber.d("SplashActivity destroyed")
    }
    
    /**
     * 重置动画状态
     */
    private fun resetAnimationState() {
        binding.ivLogo.animate().cancel()
        binding.tvAppName.animate().cancel()
        binding.tvSubtitle.animate().cancel()
        
        // 重置视图状态，避免下次启动时状态异常
        binding.ivLogo.alpha = 0f
        binding.ivLogo.scaleX = 0.5f
        binding.ivLogo.scaleY = 0.5f
        binding.tvAppName.alpha = 0f
        binding.tvSubtitle.alpha = 0f
    }
}
