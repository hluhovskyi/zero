package com.hluhovskyi.zero.budget.over

import com.hluhovskyi.zero.common.Amount

fun interface OnReallocateCompletedHandler {
    fun onComplete(sourceName: String, targetName: String, amount: Amount)

    companion object {
        val Noop = OnReallocateCompletedHandler { _, _, _ -> }
    }
}
