package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Id

data class ImportAccount(
    val id: Id.Known,
    val name: String
)