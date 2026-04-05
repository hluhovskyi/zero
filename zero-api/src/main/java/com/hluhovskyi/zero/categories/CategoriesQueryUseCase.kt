package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

/**
 * ## Ranking ([queryRanked])
 *
 * ```
 * score = (frequency × recencyDecay) × accountMultiplier × monthMultiplier × amountMultiplier
 * ```
 * - **recencyDecay** — exponential decay, 30-day half-life.
 * - **accountMultiplier** (1.0–2.0) — ratio of account-specific to global frequency.
 * - **monthMultiplier** (1.0–1.5) — ratio of same-calendar-month to global frequency, weighted 0.5.
 * - **amountMultiplier** (1.0–1.75) — log-scale Gaussian proximity to category's average amount (σ=1.0, weight 0.75).
 *
 * Categories with no usage are appended alphabetically.
 *
 * Signals use [runningFold] → [flatMapLatest]: each signal updates one field in
 * a snapshot; a new snapshot cancels prior data subscriptions and re-combines.
 * Passing [emptyFlow] is valid — all multipliers default to 1.0.
 *
 * Null in any [RankSignal] variant means "no context" → multiplier 1.0.
 */
interface CategoriesQueryUseCase {

    fun queryById(id: Id.Known): Flow<Category>

    fun queryAll(): Flow<List<Category>>

    fun queryRanked(signals: Flow<RankSignal>): Flow<List<Category>>

    data class Category(
        override val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    ) : Identifiable

    sealed class RankSignal {
        data class AccountChanged(val accountId: Id.Known?) : RankSignal()
        data class DateChanged(val date: LocalDate?) : RankSignal()
        data class AmountChanged(val amount: BigDecimal?) : RankSignal()
    }
}
