package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.castingFlowOf
import com.hluhovskyi.zero.common.coroutines.castingFlowOfNonNull
import kotlinx.coroutines.flow.Flow
import androidx.compose.ui.graphics.Color as ComposeColor

internal class PredefinedMaterialColorRepository : ColorRepository {

    private val colors = mapOf(
        color(
            id = ColorRepository.unknownCategoryColorId().value,
            hex = ComposeColor.Gray.value
        ),

        color(id = "blue", hex = ComposeColor.Blue.value),
        color(id = "red", hex = ComposeColor.Red.value),
    )

    override fun <T> query(criteria: ColorRepository.Criteria<T>): Flow<T> =
        when (criteria) {
            is ColorRepository.Criteria.All -> castingFlowOf(colors.values.toList())
            is ColorRepository.Criteria.ById -> castingFlowOfNonNull(colors[criteria.id])
        }

    private fun color(id: String, hex: ULong) = Id(id).let { knownId ->
        knownId to Color(
            id = knownId,
            value = ColorValue(hex = hex)
        )
    }
}