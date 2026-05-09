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
        color(id = "green", hex = 0xFF2E7D32UL),
        color(id = "purple", hex = 0xFF6A1B9AUL),
        color(id = "teal", hex = 0xFF00695CUL),
        color(id = "pink", hex = 0xFFAD1457UL),
        color(id = "grey", hex = 0xFF424242UL),
    )

    private val schemes = mapOf(
        Id("blue") to ColorScheme(
            swatch = colors.getValue(Id("blue")),
            primary = Color(id = Id("blue_primary"), value = ColorValue(0xFF1565C0UL)),
            background = Color(id = Id("blue_background"), value = ColorValue(0xFFE3F2FDUL)),
        ),
        Id("red") to ColorScheme(
            swatch = colors.getValue(Id("red")),
            primary = Color(id = Id("red_primary"), value = ColorValue(0xFFB71C1CUL)),
            background = Color(id = Id("red_background"), value = ColorValue(0xFFFFEBEEUL)),
        ),
        Id("orange") to ColorScheme(
            swatch = colors.getValue(Id("orange")),
            primary = Color(id = Id("orange_primary"), value = ColorValue(0xFFE65100UL)),
            background = Color(id = Id("orange_background"), value = ColorValue(0xFFFFF3E0UL)),
        ),
        Id("green") to ColorScheme(
            swatch = colors.getValue(Id("green")),
            primary = Color(id = Id("green_primary"), value = ColorValue(0xFF1B5E20UL)),
            background = Color(id = Id("green_background"), value = ColorValue(0xFFE8F5E9UL)),
        ),
        Id("purple") to ColorScheme(
            swatch = colors.getValue(Id("purple")),
            primary = Color(id = Id("purple_primary"), value = ColorValue(0xFF4A148CUL)),
            background = Color(id = Id("purple_background"), value = ColorValue(0xFFF3E5F5UL)),
        ),
        Id("teal") to ColorScheme(
            swatch = colors.getValue(Id("teal")),
            primary = Color(id = Id("teal_primary"), value = ColorValue(0xFF006064UL)),
            background = Color(id = Id("teal_background"), value = ColorValue(0xFFE0F7FAUL)),
        ),
        Id("pink") to ColorScheme(
            swatch = colors.getValue(Id("pink")),
            primary = Color(id = Id("pink_primary"), value = ColorValue(0xFFAD1457UL)),
            background = Color(id = Id("pink_background"), value = ColorValue(0xFFFCE4ECUL)),
        ),
        Id("grey") to ColorScheme(
            swatch = colors.getValue(Id("grey")),
            primary = Color(id = Id("grey_primary"), value = ColorValue(0xFF424242UL)),
            background = Color(id = Id("grey_background"), value = ColorValue(0xFFF5F5F5UL)),
        ),
    )

    private val fallbackScheme = ColorScheme(
        swatch = Color(id = Id("fallback"), value = ColorValue(0xFF424242UL)),
        primary = Color(id = Id("fallback_primary"), value = ColorValue(0xFF424242UL)),
        background = Color(id = Id("fallback_background"), value = ColorValue(0xFFF5F5F5UL)),
    )

    override fun <T> query(criteria: ColorRepository.Criteria<T>): Flow<T> = when (criteria) {
        is ColorRepository.Criteria.All -> castingFlowOf(colors.values.toList())
        is ColorRepository.Criteria.ById -> castingFlowOfNonNull(colors[criteria.id])
        is ColorRepository.Criteria.AllSchemes -> castingFlowOf(schemes.values.toList())
    }

    override fun schemeFor(colorId: Id.Known): ColorScheme = schemes[colorId] ?: fallbackScheme

    private fun color(id: String, hex: ULong) = Id(id).let { knownId ->
        knownId to Color(
            id = knownId,
            value = ColorValue(hex = hex),
        )
    }
}
