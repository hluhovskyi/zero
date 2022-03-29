package com.hluhovskyi.zero.common

import androidx.compose.ui.graphics.Color as ComposeColor

fun ColorValue.toCompose(): ComposeColor = ComposeColor(value = hex)