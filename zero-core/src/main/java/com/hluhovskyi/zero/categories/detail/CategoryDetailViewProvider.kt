package com.hluhovskyi.zero.categories.detail

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class CategoryDetailViewProvider(
    private val viewModel: CategoryDetailViewModel,
    private val transactionComponent: TransactionComponent,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        val state by viewModel.state.collectAsState(initial = CategoryDetailViewModel.State())
        val colorScheme = state.categoryColorScheme.toUi()

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopBar(state.categoryName, viewModel)
                HeroCard(state, colorScheme, imageLoader, amountFormatter)
                Box(Modifier.weight(1f)) {
                    transactionComponent.AttachWithView()
                }
            }
            ExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 32.dp),
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add transaction") },
                onClick = { viewModel.perform(CategoryDetailViewModel.Action.CreateTransaction) },
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
            )
        }
    }
}

@Composable
private fun TopBar(categoryName: String, viewModel: CategoryDetailViewModel) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { viewModel.perform(CategoryDetailViewModel.Action.Back) }) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = PrimaryContainer,
            )
        }
        Text(
            text = categoryName,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryContainer,
            ),
        )
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
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
                        viewModel.perform(CategoryDetailViewModel.Action.Edit)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Edit category")
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: CategoryDetailViewModel.State,
    colorScheme: UiColorScheme,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    Box(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colorScheme.background)
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .alpha(0.15f)
                .size(80.dp),
        ) {
            imageLoader.View(
                image = state.categoryIcon,
                modifier = Modifier.fillMaxSize(),
                tint = colorScheme.primary,
            )
        }

        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = state.periodDate
                    ?.toJavaLocalDate()
                    ?.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
                    ?.uppercase()
                    .orEmpty(),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary.copy(alpha = 0.8f),
                    letterSpacing = 1.2.sp,
                ),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = amountFormatter.format(state.totalAmount, state.currencySymbol),
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colorScheme.primary,
                    letterSpacing = (-0.72).sp,
                ),
            )
            Spacer(Modifier.size(16.dp))
            Row {
                StatColumn(
                    label = "TRANSACTIONS",
                    value = state.transactionCount.toString(),
                    colorScheme = colorScheme,
                )
                Spacer(Modifier.width(24.dp))
                StatColumn(
                    label = "AVG PER TX",
                    value = amountFormatter.format(state.averageAmount, state.currencySymbol),
                    colorScheme = colorScheme,
                )
                Spacer(Modifier.width(24.dp))
                StatColumn(
                    label = "LARGEST",
                    value = amountFormatter.format(state.largestAmount, state.currencySymbol),
                    colorScheme = colorScheme,
                )
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, colorScheme: UiColorScheme) {
    Column {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary.copy(alpha = 0.7f),
                letterSpacing = 1.2.sp,
            ),
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = value,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
            ),
        )
    }
}
