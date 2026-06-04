package com.hluhovskyi.zero.transactions.edit.common

import android.app.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.ui.SwipeSelectTile
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import java.time.format.DateTimeFormatter

private val DateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy")

@Composable
private fun TileFace(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = ZeroTheme.colors.primaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Account tile: swipe up/down to walk the [accounts] list, bouncing at the edges. */
@Composable
internal fun AccountSwipeTile(
    modifier: Modifier,
    label: String,
    accounts: List<TransactionEditAccount>,
    selected: TransactionEditAccount?,
    onSelect: (TransactionEditAccount) -> Unit,
) {
    val index = accounts.indexOf(selected)
    SwipeSelectTile(
        modifier = modifier,
        label = label,
        canSelectPrevious = index > 0,
        canSelectNext = index in 0 until accounts.lastIndex,
        currentKey = selected?.id,
        onSelectPrevious = { onSelect(accounts[index - 1]) },
        onSelectNext = { onSelect(accounts[index + 1]) },
        previous = accounts.getOrNull(index - 1)?.let { acc -> { TileFace(acc.name) } },
        next = accounts.getOrNull(index + 1)?.let { acc -> { TileFace(acc.name) } },
        current = { TileFace(selected?.name ?: "") },
    )
}

/** Date tile: swipe ±1 day (unbounded); tap opens the calendar for any other date. */
@Composable
internal fun DateSwipeTile(
    modifier: Modifier,
    label: String,
    date: LocalDateTime,
    onDateSelected: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current

    fun shift(days: Long): LocalDateTime =
        date.toJavaLocalDateTime().toLocalDate().plusDays(days).let {
            LocalDateTime(it.year, it.monthValue, it.dayOfMonth, 0, 0, 0)
        }

    fun face(value: LocalDateTime): @Composable () -> Unit = {
        TileFace(value.toJavaLocalDateTime().format(DateFormat))
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
