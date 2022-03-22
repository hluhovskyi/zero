package com.hluhovskyi.zero.common

import androidx.compose.ui.graphics.Color as ComposeColor

fun Color.toCompose(): ComposeColor = ComposeColor(value = hex)