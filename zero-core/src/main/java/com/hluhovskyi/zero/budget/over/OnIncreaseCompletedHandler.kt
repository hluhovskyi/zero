package com.hluhovskyi.zero.budget.over

import com.hluhovskyi.zero.common.Amount

fun interface OnIncreaseCompletedHandler {
    fun onComplete(targetName: String, newAmount: Amount)

    companion object {
        val Noop = OnIncreaseCompletedHandler { _, _ -> }
    }
}
