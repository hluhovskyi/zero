package com.hluhovskyi.zero.colors

import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.castingFlowOf
import com.hluhovskyi.zero.common.coroutines.castingFlowOfNonNull
import kotlinx.coroutines.flow.Flow

internal class PredefinedMaterialColorRepository : ColorRepository {

    private val colors = mapOf(
        color(
            id = ColorRepository.unknownCategoryColorId().value,
            hex = 0xFF999999UL,
        ),
        color(id = "blue", hex = 0xFF1E88E5UL),
        color(id = "red", hex = 0xFFE53935UL),
        color(id = "orange", hex = 0xFFFF9800UL),
    )

    private val schemes = mapOf(
        Id("blue") to ColorScheme(
            primary = Color(id = Id("blue_primary"), value = ColorValue(0xFF1565C0UL)),
            background = Color(id = Id("blue_background"), value = ColorValue(0xFFE3F2FDUL)),
        ),
        Id("red") to ColorScheme(
            primary = Color(id = Id("red_primary"), value = ColorValue(0xFFB71C1CUL)),
            background = Color(id = Id("red_background"), value = ColorValue(0xFFFFEBEEUL)),
        ),
        Id("orange") to ColorScheme(
            primary = Color(id = Id("orange_primary"), value = ColorValue(0xFFE65100UL)),
            background = Color(id = Id("orange_background"), value = ColorValue(0xFFFFF3E0UL)),
        ),
    )

    private val fallbackScheme = ColorScheme(
        primary = Color(id = Id("fallback_primary"), value = ColorValue(0xFF424242UL)),
        background = Color(id = Id("fallback_background"), value = ColorValue(0xFFF5F5F5UL)),
    )

    override fun <T> query(criteria: ColorRepository.Criteria<T>): Flow<T> = when (criteria) {
        is ColorRepository.Criteria.All -> castingFlowOf(colors.values.toList())
        is ColorRepository.Criteria.ById -> castingFlowOfNonNull(colors[criteria.id])
    }

    override fun schemeFor(colorId: Id.Known): ColorScheme = schemes[colorId] ?: fallbackScheme

    private fun color(id: String, hex: ULong) = Id(id).let { knownId ->
        knownId to Color(
            id = knownId,
            value = ColorValue(hex = hex),
        )
    }
}
