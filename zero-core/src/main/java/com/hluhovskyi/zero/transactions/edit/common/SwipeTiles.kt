package com.hluhovskyi.zero.transactions.edit.common

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Box
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
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = ZeroTheme.colors.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Account tile: swipe up/down to walk the [accounts] list (bounces at the edges); tap opens a
 * dropdown to jump straight to any account. */
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
        ) { account -> TileFace(account.name) }
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

/** Date tile: swipe ±1 day (unbounded); tap opens the calendar for any other date. */
@Composable
internal fun DateSwipeTile(
    modifier: Modifier,
    label: String,
    date: LocalDateTime,
    onDateSelected: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current

    fun shift(days: Long): LocalDateTime = date.toJavaLocalDateTime().toLocalDate().plusDays(days).let {
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
