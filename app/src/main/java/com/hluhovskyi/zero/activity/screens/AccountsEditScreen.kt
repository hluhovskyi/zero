package com.hluhovskyi.zero.activity.screens

import androidx.compose.runtime.Composable
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable

@Composable
fun AccountsEditScreen(
    component: Buildable<out AttachableViewComponent>
) {
    component.AttachWithView()
}