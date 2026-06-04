package com.hluhovskyi.zero.transactions.edit.common

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.ui.SwipeSelectTile
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val DayMonthFormat = DateTimeFormatter.ofPattern("d MMM")
private val WeekdayFormat = DateTimeFormatter.ofPattern("EEEE")
private val YearFormat = DateTimeFormatter.ofPattern("yyyy")

/** Two-line tile face: bold primary line over a muted secondary line (matches the Category tile's
 * value styling, extended to a second row). */
@Composable
private fun TwoRowFace(primary: String, secondary: String, secondaryColor: Color) {
    Column {
        Text(
            text = primary,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = secondary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = secondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Account tile: swipe up/down to walk the [accounts] list (bounces at the edges); tap opens a
 * dropdown to jump straight to any account. Face shows the account name over its balance. */
@Composable
internal fun AccountSwipeTile(
    modifier: Modifier,
    label: String,
    accounts: List<TransactionEditAccount>,
    selected: TransactionEditAccount?,
    onSelect: (TransactionEditAccount) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        SwipeSelectTile(
            modifier = Modifier.fillMaxWidth(),
            label = label,
            items = accounts,
            selected = selected,
            onSelect = onSelect,
            onClick = { expanded = true },
            key = { it.id },
        ) { account ->
            TwoRowFace(
                primary = account.name,
                secondary = account.balance,
                secondaryColor = if (account.isNegative) ZeroTheme.colors.error else ZeroTheme.colors.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account.name) },
                    onClick = {
                        onSelect(account)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Date tile: swipe ±1 day (unbounded); tap opens the calendar for any other date. Face shows a
 * relative word (Today / Yesterday / Tomorrow / weekday) over the calendar date. */
@Composable
internal fun DateSwipeTile(
    modifier: Modifier,
    label: String,
    date: LocalDateTime,
    onDateSelected: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val todayLabel = stringResource(R.string.transaction_edit_date_today)
    val yesterdayLabel = stringResource(R.string.transaction_edit_date_yesterday)
    val tomorrowLabel = stringResource(R.string.transaction_edit_date_tomorrow)

    fun shift(days: Long): LocalDateTime = date.toJavaLocalDateTime().toLocalDate().plusDays(days).let {
        LocalDateTime(it.year, it.monthValue, it.dayOfMonth, 0, 0, 0)
    }

    fun face(value: LocalDateTime): @Composable () -> Unit = {
        val (primary, secondary) = dateLabels(value, today, todayLabel, yesterdayLabel, tomorrowLabel)
        TwoRowFace(primary, secondary, ZeroTheme.colors.onSurfaceVariant)
    }

    SwipeSelectTile(
        modifier = modifier,
        label = label,
        currentKey = date,
        onSelectPrevious = { onDateSelected(shift(-1)) },
        onSelectNext = { onDateSelected(shift(1)) },
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onDateSelected(LocalDateTime(year, month + 1, dayOfMonth, 0, 0, 0))
                },
                date.year,
                date.monthNumber - 1,
                date.dayOfMonth,
            ).show()
        },
        previous = face(shift(-1)),
        next = face(shift(1)),
        current = face(date),
    )
}

/** Primary/secondary lines for a date: relative word + day-month near today, weekday + day-month
 * within a week, else day-month + year. */
private fun dateLabels(
    value: LocalDateTime,
    today: LocalDate,
    todayLabel: String,
    yesterdayLabel: String,
    tomorrowLabel: String,
): Pair<String, String> {
    val date = value.toJavaLocalDateTime().toLocalDate()
    val days = ChronoUnit.DAYS.between(today, date)
    val dayMonth = date.format(DayMonthFormat)
    return when {
        days == 0L -> todayLabel to dayMonth
        days == -1L -> yesterdayLabel to dayMonth
        days == 1L -> tomorrowLabel to dayMonth
        abs(days) <= 6 -> date.format(WeekdayFormat) to dayMonth
        else -> dayMonth to date.format(YearFormat)
    }
}
