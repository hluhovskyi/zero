package com.hluhovskyi.zero.common

import java.util.UUID as JavaUUID

interface IdGenerator {

    operator fun invoke(): Id.Known

    object UUID : IdGenerator {
        override fun invoke(): Id.Known = Id(JavaUUID.randomUUID().toString())
    }
}
