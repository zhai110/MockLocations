package com.mockloc.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mockloc.R
import com.mockloc.databinding.ActivitySettingsBinding
import com.mockloc.ui.update.UpdateDialogFragment
import com.mockloc.util.PrefsConfig
import com.mockloc.util.UIFeedbackHelper
import com.mockloc.util.UpdateChecker
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 设置页面
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences(PrefsConfig.SETTINGS, MODE_PRIVATE)

        setupToolbar()
        setupMovementSettings()
        setupLocationSettings()
        setupOtherSettings()
        setupResetButton()
        loadSettings()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupMovementSettings() {
        // 步行速度 (3-13 km/h)
        binding.seekbarWalkingSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 3
                // 实时显示速度值
                binding.textWalkingSpeed.text = "约 $speed km/h"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = binding.seekbarWalkingSpeed.progress + 3
                prefs.edit().putInt("walk_speed", speed).apply()
                Timber.d("Walking speed saved: $speed km/h")
            }
        })

        // 跑步速度 (5-25 km/h)
        binding.seekbarRunningSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 5
                // 实时显示速度值
                binding.textRunningSpeed.text = "约 $speed km/h"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = binding.seekbarRunningSpeed.progress + 5
                prefs.edit().putInt("run_speed", speed).apply()
                Timber.d("Running speed saved: $speed km/h")
            }
        })

        // 骑行速度 (10-40 km/h)
        binding.seekbarCyclingSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress + 10
                // 实时显示速度值
                binding.textCyclingSpeed.text = "约 $speed km/h"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val speed = binding.seekbarCyclingSpeed.progress + 10
                prefs.edit().putInt("bike_speed", speed).apply()
                Timber.d("Cycling speed saved: $speed km/h")
            }
        })
    }

    private fun setupLocationSettings() {
        // 海拔设置
        binding.itemAltitude.setOnClickListener {
            showAltitudeDialog()
        }
        
        // 位置更新频率
        binding.itemLocationUpdateInterval.setOnClickListener {
            showLocationUpdateIntervalDialog()
        }

        // 随机偏移开关
        binding.switchRandomOffset.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("random_offset", isChecked).apply()
            Timber.d("Random offset saved: $isChecked")
        }
        
        // 摇杆类型
        binding.itemJoystickType.setOnClickListener {
            showJoystickTypeDialog()
        }
        
        // 触觉反馈开关
        binding.switchJoystickHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("joystick_haptic", isChecked).apply()
            Timber.d("Joystick haptic feedback saved: $isChecked")
        }
    }

    private fun showAltitudeDialog() {
        val currentAltitude = prefs.getFloat("altitude", 0f)
        val input = android.widget.EditText(this).apply {
            setText(currentAltitude.toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("设置海拔")
            .setMessage("请输入海拔高度（米）")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val altitude = input.text.toString().toFloatOrNull() ?: 0f
                prefs.edit().putFloat("altitude", altitude).apply()
                Timber.d("Altitude saved: $altitude")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示位置更新频率选择对话框
     */
    private fun showLocationUpdateIntervalDialog() {
        val options = arrayOf("极速模式", "快速模式", "标准模式", "省电模式")
        val intervals = longArrayOf(50L, 100L, 200L, 500L)
        val currentInterval = prefs.getLong("location_update_interval", 100L)
        val currentIndex = intervals.indexOf(currentInterval).takeIf { it >= 0 } ?: 1 // 默认选"快速"
            
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("更新频率")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val interval = intervals[which]
                prefs.edit().putLong("location_update_interval", interval).apply()
                // 更新显示文本
                binding.textLocationUpdateInterval.text = options[which]
                Timber.d("Location update interval saved: ${interval}ms")
                dialog.dismiss()
            }
            .show()
    }

    private fun setupOtherSettings() {
        // 开机自启开关
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_start", isChecked).apply()
            Timber.d("Auto start saved: $isChecked")
        }
        
        // 历史记录有效期
        binding.itemHistoryExpiry.setOnClickListener {
            showHistoryExpiryDialog()
        }
        
        // 日志开关
        binding.switchLog.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("logging", isChecked).apply()
            Timber.d("Logging saved: $isChecked")
        }

        // 关于
        binding.itemAbout.setOnClickListener {
            showAboutDialog()
        }
    }

    private fun showJoystickTypeDialog() {
        val types = arrayOf("摇杆模式", "按钮模式")
        val currentType = prefs.getInt("joystick_type", 0)
        
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("摇杆类型")
            .setSingleChoiceItems(types, currentType) { dialog, which ->
                prefs.edit().putInt("joystick_type", which).apply()
                // 更新显示文本
                binding.textJoystickType.text = types[which]
                Timber.d("Joystick type saved: ${types[which]}")
                dialog.dismiss()
            }
            .show()
    }

    private fun showHistoryExpiryDialog() {
        val options = arrayOf("7天有效", "14天有效", "30天有效", "永久保存")
        val expiryDays = intArrayOf(7, 14, 30, -1)
        val currentExpiry = prefs.getInt("history_expiry", 30)
        val currentIndex = when (currentExpiry) {
            7 -> 0
            14 -> 1
            30 -> 2
            else -> 3
        }
        
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("有效记录")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                val expiry = expiryDays[which]
                prefs.edit().putInt("history_expiry", expiry).apply()
                // 更新显示文本
                binding.textHistoryExpiry.text = options[which]
                Timber.d("History expiry saved: ${options[which]}")
                dialog.dismiss()
            }
            .show()
    }

    private fun showAboutDialog() {
        val updateChecker = UpdateChecker(this)
        val (versionCode, versionName) = updateChecker.getCurrentVersionInfo()
        
        AlertDialog.Builder(this, R.style.RoundedDialogTheme)
            .setTitle("关于")
            .setMessage("${getString(R.string.about_message)}\n\n当前版本：$versionName ($versionCode)")
            .setPositiveButton("确定", null)
            .setNeutralButton("检查更新") { _, _ ->
                checkForUpdate()
            }
            .show()
    }
    
    /**
     * 检查更新
     */
    private fun checkForUpdate() {
        val updateChecker = UpdateChecker(this)
        
        lifecycleScope.launch {
            // 显示加载提示
            val progressDialog = android.app.ProgressDialog(this@SettingsActivity).apply {
                setMessage("检查更新中...")
                setCancelable(false)
                show()
            }
            
            updateChecker.checkForUpdate()
                .onSuccess { updateInfo ->
                    progressDialog.dismiss()
                    
                    if (updateInfo != null) {
                        // 有新版本，显示更新对话框
                        UpdateDialogFragment.newInstance(updateInfo)
                            .show(supportFragmentManager, "update_dialog")
                    } else {
                        // 已是最新版本
                        UIFeedbackHelper.showToast(this@SettingsActivity, "当前已是最新版本")
                    }
                }
                .onFailure { error ->
                    progressDialog.dismiss()
                    UIFeedbackHelper.showToast(this@SettingsActivity, "检查更新失败：${error.message}")
                    Timber.e(error, "Update check failed")
                }
        }
    }

    private fun setupResetButton() {
        binding.btnResetDefaults.setOnClickListener {
            AlertDialog.Builder(this, R.style.ResetDefaultsDialogTheme)
                .setTitle("恢复默认设置")
                .setMessage("此操作将清除所有自定义设置并恢复为默认值，是否继续？")
                .setPositiveButton("确定") { _, _ ->
                    // 清除所有 SharedPreferences 数据
                    prefs.edit().clear().apply()
                    Timber.d("All settings cleared, restored to defaults")
                    
                    // 重新加载默认设置到 UI
                    loadSettings()
                    
                    // 显示提示
                    android.widget.Toast.makeText(this, "已恢复默认设置", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun loadSettings() {
        // 加载速度设置
        val walkSpeed = prefs.getInt("walk_speed", 5)
        val runSpeed = prefs.getInt("run_speed", 12)
        val bikeSpeed = prefs.getInt("bike_speed", 20)
        
        binding.seekbarWalkingSpeed.progress = walkSpeed - 3
        binding.seekbarRunningSpeed.progress = runSpeed - 5
        binding.seekbarCyclingSpeed.progress = bikeSpeed - 10
        
        // 更新速度显示文本
        binding.textWalkingSpeed.text = "约 $walkSpeed km/h"
        binding.textRunningSpeed.text = "约 $runSpeed km/h"
        binding.textCyclingSpeed.text = "约 $bikeSpeed km/h"
        
        // 加载开关设置
        binding.switchRandomOffset.isChecked = prefs.getBoolean("random_offset", false)
        binding.switchAutoStart.isChecked = prefs.getBoolean("auto_start", false)
        binding.switchLog.isChecked = prefs.getBoolean("logging", true)
        binding.switchJoystickHaptic.isChecked = prefs.getBoolean("joystick_haptic", true)
        
        // 加载摇杆类型显示
        val joystickType = prefs.getInt("joystick_type", 0)
        binding.textJoystickType.text = if (joystickType == 0) "摇杆模式" else "按钮模式"
        
        // 加载历史记录有效期显示
        val historyExpiry = prefs.getInt("history_expiry", 30)
        binding.textHistoryExpiry.text = when (historyExpiry) {
            7 -> "7天有效"
            14 -> "14天有效"
            30 -> "30天有效"
            else -> "永久保存"
        }
        
        // 加载位置更新频率显示
        val locationUpdateInterval = prefs.getLong("location_update_interval", 100L)
        binding.textLocationUpdateInterval.text = when (locationUpdateInterval) {
            50L -> "极速模式"
            100L -> "快速模式"
            200L -> "标准模式"
            500L -> "省电模式"
            else -> "快速模式"
        }
        
        Timber.d("Settings loaded: walk=$walkSpeed, run=$runSpeed, bike=$bikeSpeed")
    }

    /**
     * 处理配置变化（夜间模式切换）
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        val isNight = (newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        Timber.d("SettingsActivity configuration changed: isNight=$isNight")
        
        // ✅ 重新加载设置以应用新主题的颜色
        // 由于使用了 ViewBinding，大部分颜色会自动更新
        // 这里主要是为了确保 Toolbar 等组件正确刷新
        loadSettings()
    }
}
