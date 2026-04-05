package com.hluhovskyi.zero.categories

import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.Identifiable
import com.hluhovskyi.zero.common.Image
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Provides resolved categories (with icons and color schemes) for display.
 *
 * Three query modes:
 * - [queryAll] — unordered list, used by screens that show all categories.
 * - [queryById] — single category, used when only one is needed.
 * - [queryRanked] — scored and sorted by usage patterns, used in transaction edit.
 *
 * ## Ranking
 *
 * [queryRanked] accepts a [Flow] of [RankSignal]s that the caller emits as
 * form state changes (account selected, date picked, amount typed). The
 * implementation maintains a running snapshot of the latest signal values
 * and re-ranks reactively.
 *
 * Scoring formula per category:
 * ```
 * score = (frequency × recencyDecay) × accountMultiplier × monthMultiplier × amountMultiplier
 * ```
 * - **recencyDecay** — exponential decay with 30-day half-life.
 * - **accountMultiplier** (1.0–2.0) — boosts categories historically used with the selected account.
 * - **monthMultiplier** (1.0–1.5) — boosts categories used in the same calendar month (seasonal patterns).
 * - **amountMultiplier** (1.0–1.75) — boosts categories whose average transaction amount is close
 *   to the entered amount (log-scale Gaussian, σ=1.0).
 *
 * Categories with no usage history are appended alphabetically at the end.
 *
 * Passing [emptyFlow] as signals is valid — ranking uses only the base score
 * (frequency × recency) with all multipliers at 1.0.
 *
 * @see DefaultCategoriesQueryUseCase for implementation details.
 */
interface CategoriesQueryUseCase {

    fun queryById(id: Id.Known): Flow<Category>

    fun queryAll(): Flow<List<Category>>

    /**
     * Returns categories sorted by a composite score that reacts to [signals].
     *
     * Each [RankSignal] updates one dimension of context. The implementation
     * uses [runningFold] to track the latest values and [flatMapLatest] to
     * re-subscribe to the appropriate data queries when context changes.
     *
     * @param signals a flow of contextual changes from the transaction edit form.
     *   May be [emptyFlow] when no contextual ranking is needed.
     */
    fun queryRanked(signals: Flow<RankSignal>): Flow<List<Category>>

    data class Category(
        override val id: Id.Known,
        val name: String,
        val icon: Image,
        val colorScheme: ColorScheme,
    ) : Identifiable

    /**
     * Contextual signal emitted by the transaction edit form to influence ranking.
     *
     * Each variant carries a nullable value — null means "no context for this
     * dimension", which makes the corresponding multiplier default to 1.0.
     */
    sealed class RankSignal {
        /** Selected account changed. Boosts categories historically used with this account. */
        data class AccountChanged(val accountId: Id.Known?) : RankSignal()
        /** Transaction date changed. Boosts categories used in the same calendar month. */
        data class DateChanged(val date: LocalDate?) : RankSignal()
        /** Entered amount changed. Boosts categories with a similar average transaction amount. */
        data class AmountChanged(val amount: BigDecimal?) : RankSignal()
    }
}
