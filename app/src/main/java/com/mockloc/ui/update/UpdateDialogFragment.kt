package com.mockloc.ui.update

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mockloc.R
import com.mockloc.util.UpdateChecker
import com.mockloc.util.UpdateInfo
import com.mockloc.util.UIFeedbackHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * 更新对话框
 * 
 * 显示新版本信息，支持下载和安装 APK
 */
class UpdateDialogFragment : DialogFragment() {
    
    companion object {
        private const val ARG_UPDATE_INFO = "update_info"
        
        /**
         * 创建实例
         */
        fun newInstance(updateInfo: UpdateInfo): UpdateDialogFragment {
            return UpdateDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_UPDATE_INFO, updateInfo)
                }
                isCancelable = !updateInfo.forceUpdate
            }
        }
    }
    
    private var updateInfo: UpdateInfo? = null
    private var updateChecker: UpdateChecker? = null
    private var downloadJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateInfo = arguments?.getSerializable(ARG_UPDATE_INFO) as? UpdateInfo
        updateChecker = context?.let { UpdateChecker(it) }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val info = updateInfo ?: throw IllegalStateException("UpdateInfo is null")
        val context = requireContext()
        
        //  inflate 布局
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_update, null)
        
        // 初始化视图
        val tvVersion = view.findViewById<TextView>(R.id.tv_version)
        val tvReleaseNotes = view.findViewById<TextView>(R.id.tv_release_notes)
        val tvFileSize = view.findViewById<TextView>(R.id.tv_file_size)
        val btnUpdate = view.findViewById<MaterialButton>(R.id.btn_update)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val tvProgress = view.findViewById<TextView>(R.id.tv_progress)
        
        // 设置内容
        tvVersion.text = "新版本：${info.versionName}"
        tvReleaseNotes.text = info.releaseNotes.replace("\\n", "\n")
        tvFileSize.text = "文件大小：${info.getFileSizeFormatted()}"
        
        // 如果是强制更新，隐藏取消按钮
        if (info.forceUpdate) {
            btnCancel.visibility = View.GONE
        }
        
        // 创建对话框
        val dialog = MaterialAlertDialogBuilder(context, R.style.RoundedDialogTheme)
            .setView(view)
            .setCancelable(!info.forceUpdate)
            .create()
        
        // 按钮点击事件
        btnUpdate.setOnClickListener {
            btnUpdate.isEnabled = false
            btnCancel.isEnabled = false
            progressBar.visibility = View.VISIBLE
            tvProgress.visibility = View.VISIBLE
            
            // 开始下载
            downloadApk(info, progressBar, tvProgress, btnUpdate, btnCancel)
        }
        
        btnCancel.setOnClickListener {
            if (info.forceUpdate) {
                UIFeedbackHelper.showToast(context, "此版本必须更新")
            } else {
                dismiss()
            }
        }
        
        return dialog
    }
    
    /**
     * 下载 APK
     */
    private fun downloadApk(
        info: UpdateInfo,
        progressBar: ProgressBar,
        tvProgress: TextView,
        btnUpdate: MaterialButton,
        btnCancel: MaterialButton
    ) {
        // ✅ 创建主线程 Handler 用于更新 UI
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        downloadJob = CoroutineScope(Dispatchers.Main).launch {
            val checker = updateChecker ?: return@launch
            
            // ✅ 使用 Handler 确保进度回调在主线程执行
            checker.downloadApk(info) { progress ->
                mainHandler.post {
                    progressBar.progress = progress
                    tvProgress.text = "下载中... $progress%"
                }
            }.onSuccess { apkFile ->
                // 下载成功，触发安装
                Timber.i("APK downloaded: ${apkFile.absolutePath}")
                installApk(apkFile)
                dismiss()
            }.onFailure { error ->
                // 下载失败
                UIFeedbackHelper.showToast(requireContext(), "下载失败：${error.message}")
                btnUpdate.isEnabled = true
                btnCancel.isEnabled = true
                progressBar.visibility = View.GONE
                tvProgress.visibility = View.GONE
                Timber.e(error, "APK download failed")
            }
        }
    }
    
    /**
     * 安装 APK
     */
    private fun installApk(apkFile: File) {
        try {
            val context = requireContext()
            
            // 获取 APK URI
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            // 创建安装 Intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(intent)
            Timber.i("Installation intent started")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to install APK")
            UIFeedbackHelper.showToast(requireContext(), "安装失败：${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消下载任务
        downloadJob?.cancel()
        updateChecker?.cancelDownload()
    }
}
