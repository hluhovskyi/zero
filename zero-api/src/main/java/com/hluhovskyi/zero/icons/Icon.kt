package com.hluhovskyi.zero.icons

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image

data class Icon(
    override val id: Id.Known,
    val image: Image,
) : Identifiable {

    companion object {

        fun empty(): Icon = Icon(
            id = Id("empty_icon"),
            image = Image.empty(),
        )
    }
}
