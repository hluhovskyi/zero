package com.hluhovskyi.zero.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun trackChanges(tag: String, any: Any?) {
    val previous = remember { mutableStateOf(any) }

    SideEffect {
        if (previous.value !== any) {
            Log.d("Recompose", "$tag, ${previous.value} -> $any")
            previous.value = any
        }
    }
}
