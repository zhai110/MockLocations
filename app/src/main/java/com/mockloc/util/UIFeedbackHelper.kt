package com.mockloc.util

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.mockloc.R

/**
 * UI反馈工具类
 * 统一处理Toast、Snackbar等用户提示
 */
object UIFeedbackHelper {

    /**
     * 显示Snackbar（推荐方式）
     * 
     * @param view 父视图
     * @param message 提示消息
     * @param duration 显示时长
     * @param actionText 操作按钮文字
     * @param action 操作按钮点击回调
     * @param backgroundColor 背景颜色（null则使用默认surface色）
     * @param textColor 文字颜色（null则根据背景自动适配：深色背景用白色，浅色背景用深色）
     */
    fun showSnackbar(
        view: View,
        message: String,
        duration: Int = Snackbar.LENGTH_SHORT,
        actionText: String? = null,
        action: (() -> Unit)? = null,
        backgroundColor: Int? = null,
        textColor: Int? = null
    ) {
        val snackbar = Snackbar.make(view, message, duration)
        
        // 设置背景色
        val bgColor = backgroundColor ?: view.context.getColor(R.color.surface)
        snackbar.setBackgroundTint(bgColor)
        
        // 自动适配文字颜色（如果未指定）
        val txtColor = textColor ?: if (backgroundColor != null) {
            // 有自定义背景色时，使用白色文字确保对比度
            view.context.getColor(R.color.white)
        } else {
            // 默认背景色时，使用主题文字色
            view.context.getColor(R.color.text_primary)
        }
        snackbar.setTextColor(txtColor)
        
        // 设置操作按钮
        actionText?.let {
            snackbar.setAction(it) {
                action?.invoke()
            }
            snackbar.setActionTextColor(txtColor)
        }
        
        snackbar.show()
    }

    /**
     * 显示成功提示Snackbar（绿色背景 + 白色文字）
     */
    fun showSuccess(view: View, message: String) {
        showSnackbar(
            view = view,
            message = "✓ $message",
            duration = Snackbar.LENGTH_SHORT,
            backgroundColor = view.context.getColor(R.color.success)
        )
    }

    /**
     * 显示错误提示Snackbar（红色背景 + 白色文字）
     */
    fun showError(view: View, message: String) {
        showSnackbar(
            view = view,
            message = "✗ $message",
            duration = Snackbar.LENGTH_LONG,
            backgroundColor = view.context.getColor(R.color.error)
        )
    }

    /**
     * 显示信息提示Snackbar（蓝色背景 + 白色文字）
     */
    fun showInfo(view: View, message: String) {
        showSnackbar(
            view = view,
            message = "ℹ $message",
            duration = Snackbar.LENGTH_SHORT,
            backgroundColor = view.context.getColor(R.color.info)
        )
    }

    /**
     * 显示警告提示Snackbar（黄色背景 + 深色文字）
     * 黄色较浅，使用深色文字确保对比度
     */
    fun showWarning(view: View, message: String) {
        showSnackbar(
            view = view,
            message = "⚠ $message",
            duration = Snackbar.LENGTH_LONG,
            backgroundColor = view.context.getColor(R.color.warning),
            textColor = view.context.getColor(R.color.text_primary)
        )
    }

    /**
     * 悬浮窗环境下的Toast（因为Snackbar需要View）
     * 优化版Toast：居中显示，带背景
     */
    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).apply {
            setGravity(Gravity.CENTER, 0, 0)
            show()
        }
    }
}
