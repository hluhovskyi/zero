package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.activity.navigation.Destination
import com.hluhovskyi.zero.common.Image

internal sealed interface BottomNavigation {
    object None : BottomNavigation

    data class WithItems(val items: List<Item>) : BottomNavigation

    data class Item(
        val name: String,
        val icon: Image,
        val destination: Destination
    )
}

