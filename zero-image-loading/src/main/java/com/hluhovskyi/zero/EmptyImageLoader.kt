package com.hluhovskyi.zero

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hluhovskyi.zero.common.Uri

internal object EmptyImageLoader : ImageLoader {

    @Composable
    override fun View(
        uri: Uri,
        contentDescription: String?,
        modifier: Modifier,
        scale: ImageLoader.Scale
    ) {
        Spacer(modifier = modifier)
    }
}