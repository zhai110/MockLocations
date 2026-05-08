
package com.mockloc.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import timber.log.Timber

inline fun &lt;reified T : Activity&gt; Context.launchActivity(block: Intent.() -&gt; Unit = {}) {
    val intent = Intent(this, T::class.java)
    intent.block()
    startActivity(intent)
}

inline fun &lt;reified T : Activity&gt; Fragment.launchActivity(block: Intent.() -&gt; Unit = {}) {
    requireContext().launchActivity&lt;T&gt;(block)
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
}

fun View.showKeyboard() {
    requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(this, 0)
}

fun View.updateMargins(
    start: Int = marginStart,
    top: Int = marginTop,
    end: Int = marginEnd,
    bottom: Int = marginBottom
) {
    updateLayoutParams&lt;ViewGroup.MarginLayoutParams&gt; {
        marginStart = start
        topMargin = top
        marginEnd = end
        bottomMargin = bottom
    }
}

fun View.applyWindowInsets(
    consumeInsets: Boolean = false,
    block: (View, WindowInsetsCompat) -&gt; Unit
) {
    setOnApplyWindowInsetsListener { view, insets -&gt;
        val compatInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
        block(view, compatInsets)
        if (consumeInsets) {
            WindowInsetsCompat.CONSUMED.toWindowInsets() ?: insets
        } else {
            insets
        }
    }
}

fun logD(message: String) = Timber.d(message)

fun logE(message: String, throwable: Throwable? = null) = Timber.e(throwable, message)

fun logW(message: String) = Timber.w(message)

fun logI(message: String) = Timber.i(message)

fun &lt;T&gt; T.safeLet(vararg elements: Any?, block: () -&gt; Unit) {
    if (elements.all { it != null }) {
        block()
    }
}

fun &lt;T : Any&gt; T?.orDefault(default: T): T = this ?: default

@Suppress("DEPRECATION")
fun &lt;T : Parcelable&gt; Intent.getParcelableExtraCompat(name: String, clazz: Class&lt;T&gt;): T? {
    return if (Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, clazz)
    } else {
        getParcelableExtra(name) as? T
    }
}

@Suppress("DEPRECATION")
fun &lt;T : Parcelable&gt; Bundle.getParcelableCompat(key: String, clazz: Class&lt;T&gt;): T? {
    return if (Build.VERSION.SDK_INT &gt;= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, clazz)
    } else {
        getParcelable(key) as? T
    }
}

