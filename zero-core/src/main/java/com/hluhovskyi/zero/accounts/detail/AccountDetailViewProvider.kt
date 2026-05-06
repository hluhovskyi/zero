package com.hluhovskyi.zero.accounts.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.ui.DetailStatColumn
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.ErrorContainer
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.Secondary
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal class AccountDetailViewProvider(
    private val viewModel: AccountDetailViewModel,
    private val transactionComponent: TransactionComponent,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = AccountDetailViewModel.State())

        val heroHeightPx = remember { mutableStateOf(0) }
        val heroOffsetPx = remember { mutableStateOf(0f) }

        val connection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y >= 0f) return Offset.Zero
                    val newOffset = (heroOffsetPx.value + available.y)
                        .coerceIn(-heroHeightPx.value.toFloat(), 0f)
                    val consumed = newOffset - heroOffsetPx.value
                    heroOffsetPx.value = newOffset
                    return Offset(0f, consumed)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y <= 0f) return Offset.Zero
                    val newOffset = (heroOffsetPx.value + available.y).coerceAtMost(0f)
                    val delta = newOffset - heroOffsetPx.value
                    heroOffsetPx.value = newOffset
                    return Offset(0f, delta)
                }
            }
        }

        Box(Modifier.fillMaxSize().nestedScroll(connection)) {
            Column(Modifier.fillMaxSize()) {
                DetailTopBar(
                    title = state.accountName,
                    onBack = { viewModel.perform(AccountDetailViewModel.Action.Back) },
                )
                Box(Modifier.weight(1f)) {
                    val topPaddingDp = with(LocalDensity.current) {
                        (heroHeightPx.value + heroOffsetPx.value).coerceAtLeast(0f).toDp()
                    }
                    Box(Modifier.fillMaxSize().padding(top = topPaddingDp)) {
                        transactionComponent.AttachWithView()
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { heroHeightPx.value = it.height }
                            .offset { IntOffset(0, heroOffsetPx.value.roundToInt()) },
                    ) {
                        HeroCard(state, amountFormatter, imageLoader)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: AccountDetailViewModel.State,
    amountFormatter: AmountFormatter,
    imageLoader: ImageLoader,
) {
    val isNeg = state.isNegativeBalance
    val heroBackground = if (isNeg) ErrorContainer else SurfaceContainerLow
    val balanceColor = if (isNeg) Error else Primary
    val accentColor = if (isNeg) Error else Primary
    val inValueColor = if (isNeg) Error else Secondary

    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(heroBackground)
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .alpha(0.08f)
                .size(80.dp),
        ) {
            imageLoader.View(
                image = state.accountIcon,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            val periodLabel = state.accountDetails?.uppercase()
                ?: state.periodDate
                    ?.toJavaLocalDate()
                    ?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                    ?.uppercase()
                    .orEmpty()

            Text(
                text = periodLabel,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor.copy(alpha = 0.75f),
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = amountFormatter.format(state.balance, state.currencySymbol),
                style = TextStyle(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = balanceColor,
                    letterSpacing = (-0.68).sp,
                ),
            )
            Spacer(Modifier.size(16.dp))
            Row {
                DetailStatColumn(
                    label = "IN THIS MONTH",
                    value = "+${amountFormatter.format(state.totalIn, state.currencySymbol)}",
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = inValueColor,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = "OUT THIS MONTH",
                    value = "–${amountFormatter.format(state.totalOut, state.currencySymbol)}",
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = OnSurface,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = "TRANSACTIONS",
                    value = state.transactionCount.toString(),
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = OnSurface,
                )
            }
        }
    }
}
