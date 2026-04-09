package com.hluhovskyi.zero.common

import androidx.compose.runtime.Composable

interface ViewProvider {

    @Composable
    fun View()

    companion object {

        inline operator fun invoke(crossinline provider: @Composable () -> Unit): ViewProvider = object : ViewProvider {
            @Composable
            override fun View() {
                provider()
            }
        }
    }
}

@Composable
operator fun ViewProvider.invoke() = View()
