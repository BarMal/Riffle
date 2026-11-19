package com.riffle.app.launcher.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The result of [load], run off the main thread whenever a key changes; [initial] until then. */
@Composable
internal fun <T> rememberLoaded(
    initial: T,
    key1: Any?,
    key2: Any? = null,
    load: () -> T,
): State<T> {
    val state = remember(key1, key2) { mutableStateOf(initial) }
    LaunchedEffect(key1, key2) { state.value = withContext(Dispatchers.Default) { load() } }
    return state
}
