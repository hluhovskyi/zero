package com.hluhovskyi.zero.common

import java.util.UUID

interface IdGenerator {

    operator fun invoke(): Id.Known

    object UUID : IdGenerator {
        override fun invoke(): Id.Known = Id(java.util.UUID.randomUUID().toString())
    }
}