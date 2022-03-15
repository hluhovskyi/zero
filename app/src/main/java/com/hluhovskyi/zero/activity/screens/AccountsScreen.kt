package com.hluhovskyi.zero.activity.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable

@Composable
fun AccountsScreen(
    component: Buildable<out AttachableViewComponent>
) {
    Box(modifier = Modifier.fillMaxSize()) {
        component.AttachWithView()
    }
}