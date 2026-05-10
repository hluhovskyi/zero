package com.hluhovskyi.zero.icons

data class IconCategory(
    val id: String,
    val name: String,
) {

    companion object {

        fun unknown(): IconCategory = IconCategory(id = "", name = "")

        fun system(): IconCategory = IconCategory(id = "system", name = "System")
    }
}
