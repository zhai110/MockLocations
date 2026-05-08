
package com.mockloc.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding

abstract class BaseActivity&lt;VB : ViewBinding&gt;(
    private val bindingInflater: (layoutInflater: android.view.LayoutInflater) -&gt; VB
) : AppCompatActivity() {

    protected lateinit var binding: VB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = bindingInflater(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        initViews()
        observeViewModel()
    }

    open fun setupWindowInsets() {
    }

    abstract fun initViews()

    open fun observeViewModel() {
    }
}

