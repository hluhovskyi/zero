package com.hluhovskyi.zero.accounts.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.ui.CollapsibleHeroLayout
import com.hluhovskyi.zero.ui.DetailStatColumn
import com.hluhovskyi.zero.ui.DetailTopBar
import com.hluhovskyi.zero.ui.theme.Error
import com.hluhovskyi.zero.ui.theme.ErrorContainer
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.Primary
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Secondary
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class AccountDetailViewProvider(
    private val viewModel: AccountDetailViewModel,
    private val transactionComponent: TransactionComponent,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = AccountDetailViewModel.State())

        Box(Modifier.fillMaxSize()) {
            CollapsibleHeroLayout(
                topBar = {
                    DetailTopBar(
                        title = state.accountName,
                        onBack = { viewModel.perform(AccountDetailViewModel.Action.Back) },
                        trailing = {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.account_detail_more_options_description),
                                    tint = PrimaryContainer,
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.perform(AccountDetailViewModel.Action.Edit)
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.account_detail_edit))
                                }
                                if (state.isArchived) {
                                    DropdownMenuItem(
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.perform(AccountDetailViewModel.Action.Unarchive)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Unarchive,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.account_detail_unarchive))
                                    }
                                } else {
                                    DropdownMenuItem(
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.perform(AccountDetailViewModel.Action.Archive)
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Archive,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.account_detail_archive))
                                    }
                                }
                            }
                        },
                    )
                },
                hero = { HeroCard(state, amountFormatter, imageLoader) },
                content = { transactionComponent.AttachWithView() },
            )
            ExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 32.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.account_detail_add_transaction)) },
                onClick = { viewModel.perform(AccountDetailViewModel.Action.CreateTransaction) },
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
            )
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
                    label = stringResource(R.string.account_detail_in_this_month),
                    value = "+${amountFormatter.format(state.totalIn, state.currencySymbol)}",
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = inValueColor,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = stringResource(R.string.account_detail_out_this_month),
                    value = "–${amountFormatter.format(state.totalOut, state.currencySymbol)}",
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = OnSurface,
                )
                Spacer(Modifier.width(24.dp))
                DetailStatColumn(
                    label = stringResource(R.string.account_detail_transactions),
                    value = state.transactionCount.toString(),
                    labelColor = accentColor.copy(alpha = 0.7f),
                    valueColor = OnSurface,
                )
            }
        }
    }
}
