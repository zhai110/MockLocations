
package com.mockloc.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

inline fun &lt;T&gt; CoroutineScope.launchWithErrorHandler(
    crossinline block: suspend CoroutineScope.() -&gt; T,
    crossinline onError: (Throwable) -&gt; Unit = { logE("Coroutine error", it) }
) = launch {
    try {
        block()
    } catch (e: Exception) {
        onError(e)
    }
}

suspend fun &lt;T&gt; withIO(block: suspend () -&gt; T) = withContext(Dispatchers.IO) { block() }

suspend fun &lt;T&gt; withMain(block: suspend () -&gt; T) = withContext(Dispatchers.Main) { block() }

suspend fun &lt;T&gt; withDefault(block: suspend () -&gt; T) = withContext(Dispatchers.Default) { block() }

fun &lt;T&gt; flowIO(crossinline block: suspend () -&gt; T): Flow&lt;T&gt; = flow {
    emit(withIO(block))
}

