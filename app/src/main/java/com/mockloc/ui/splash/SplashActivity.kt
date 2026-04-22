package com.mockloc.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.mockloc.R
import com.mockloc.databinding.ActivitySplashBinding
import com.mockloc.ui.main.MainActivity
import com.mockloc.ui.permission.PermissionGuideActivity
import com.mockloc.util.PermissionHelper
import timber.log.Timber

/**
 * 启动页 - 现代时尚风格
 * 
 * 优化点：
 * 1. 每次启动都显示完整动画序列（1.5秒）
 * 2. Logo 缩放 + 淡入效果
 * 3. 标题和副标题依次淡入
 * 4. 添加生命周期管理，防止内存泄漏
 * 5. 使用弹性插值器使动画更自然
 * 6. 锁定竖屏方向避免重建
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    
    // 动画配置常量
    companion object {
        private const val LOGO_ANIMATION_DURATION = 600L      // Logo 动画时长
        private const val TITLE_ANIMATION_DELAY = 300L        // 标题延迟
        private const val TITLE_ANIMATION_DURATION = 500L     // 标题动画时长
        private const val SUBTITLE_ANIMATION_DELAY = 500L     // 副标题延迟
        private const val SUBTITLE_ANIMATION_DURATION = 500L  // 副标题动画时长
        private const val NAVIGATION_DELAY = 1200L            // 总延迟时间（动画结束后短暂停留）
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Timber.d("SplashActivity created")

        // 启动完整动画序列
        startAnimations()
        scheduleNavigation()
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
     * Logo 淡入 + 缩放动画（从中心放大）
     */
    private fun animateLogo() {
        binding.ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(LOGO_ANIMATION_DURATION)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()
    }
    
    /**
     * 标题淡入 + 轻微上移动画
     */
    private fun animateTitle() {
        binding.tvAppName.postDelayed({
            binding.tvAppName.apply {
                translationY = 20f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(TITLE_ANIMATION_DURATION)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }, TITLE_ANIMATION_DELAY)
    }
    
    /**
     * 副标题淡入 + 轻微上移动画
     */
    private fun animateSubtitle() {
        binding.tvSubtitle.postDelayed({
            binding.tvSubtitle.apply {
                translationY = 20f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(SUBTITLE_ANIMATION_DURATION)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }, SUBTITLE_ANIMATION_DELAY)
    }
    
    /**
     * 调度页面跳转
     */
    private fun scheduleNavigation() {
        binding.root.postDelayed({
            Timber.d("Splash animation completed, navigating to main")
            navigateToMain()
        }, NAVIGATION_DELAY)
    }

    /**
     * 跳转到主界面或权限引导页
     */
    private fun navigateToMain() {
        try {
            // 检查必要权限
            val hasLocation = PermissionHelper.hasLocationPermissions(this)
            val hasOverlay = PermissionHelper.hasOverlayPermission(this)
            
            Timber.d("Permission check: location=$hasLocation, overlay=$hasOverlay")
            
            val intent = if (!hasLocation || !hasOverlay) {
                // 缺少必要权限，跳转到权限引导页
                Timber.d("Missing permissions, navigating to PermissionGuideActivity")
                Intent(this, PermissionGuideActivity::class.java)
            } else {
                // 权限齐全，直接进入主界面
                Timber.d("All permissions granted, navigating to MainActivity")
                Intent(this, MainActivity::class.java)
            }
            
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start activity")
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
