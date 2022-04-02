package com.hluhovskyi.zero.common

import androidx.compose.ui.graphics.Color as ComposeColor

fun ColorValue.toCompose(): ComposeColor =
    if (isUnspecified()) {
        ComposeColor.Unspecified
    } else {
        ComposeColor(value = hex)
    }