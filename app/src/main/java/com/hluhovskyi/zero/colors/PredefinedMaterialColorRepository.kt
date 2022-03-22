package com.hluhovskyi.zero.colors

import androidx.compose.ui.graphics.Color
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.castingFlowOf
import com.hluhovskyi.zero.common.coroutines.castingFlowOfNonNull
import kotlinx.coroutines.flow.Flow
import com.hluhovskyi.zero.common.Color as ColorValue

internal class PredefinedMaterialColorRepository : ColorRepository {

    private val colors = mapOf(
        color(id = "blue", hex = Color.Blue.value),
        color(id = "red", hex = Color.Red.value),
    )

    override fun <T> query(criteria: ColorRepository.Criteria<T>): Flow<T> =
        when (criteria) {
            is ColorRepository.Criteria.All -> castingFlowOf(colors.values.toList())
            is ColorRepository.Criteria.ById -> castingFlowOfNonNull(colors[criteria.id])
        }

    private fun color(id: String, hex: ULong) = Id(id).let { knownId ->
        knownId to ColorRepository.Color(
            id = knownId,
            color = ColorValue(hex = hex)
        )
    }
}