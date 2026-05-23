package com.hluhovskyi.zero.budget.over

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.budget.NumPad
import com.hluhovskyi.zero.ui.common.toCompose
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme
import java.math.BigDecimal

internal class BudgetOverViewProvider(
    private val viewModel: BudgetOverViewModel,
    private val imageLoader: ImageLoader,
    private val amountFormatter: AmountFormatter,
) : ViewProvider {

    @Composable
    override fun View() {
        BudgetOverSheet(
            viewModel = viewModel,
            imageLoader = imageLoader,
            amountFormatter = amountFormatter,
        )
    }
}

@Composable
private fun BudgetOverSheet(
    viewModel: BudgetOverViewModel,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
) {
    val state by viewModel.state.collectAsState(initial = BudgetOverViewModel.State())
    val target = state.target

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surface, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        if (target == null) {
            return@Column
        }
        when (state.mode) {
            BudgetOverViewModel.Mode.CHOICE -> ChoiceContent(
                target = target,
                amountFormatter = amountFormatter,
                onClose = { viewModel.perform(BudgetOverViewModel.Action.TapClose) },
                onReallocate = { viewModel.perform(BudgetOverViewModel.Action.TapReallocateOption) },
                onIncrease = { viewModel.perform(BudgetOverViewModel.Action.TapIncreaseOption) },
            )
            BudgetOverViewModel.Mode.REALLOCATE -> ReallocateContent(
                target = target,
                sources = state.reallocationSources,
                selectedSource = state.selectedSource,
                amountToMove = state.amountToMove,
                imageLoader = imageLoader,
                amountFormatter = amountFormatter,
                onBack = { viewModel.perform(BudgetOverViewModel.Action.TapBack) },
                onSelectSource = { id -> viewModel.perform(BudgetOverViewModel.Action.SelectSource(id)) },
                onConfirm = { viewModel.perform(BudgetOverViewModel.Action.ConfirmReallocate) },
            )
            BudgetOverViewModel.Mode.INCREASE -> IncreaseContent(
                target = target,
                amountText = state.increaseAmountText,
                suggestions = state.increaseSuggestions,
                newBudgeted = state.newBudgetedAfterIncrease,
                imageLoader = imageLoader,
                amountFormatter = amountFormatter,
                onBack = { viewModel.perform(BudgetOverViewModel.Action.TapBack) },
                onSuggestion = { amount -> viewModel.perform(BudgetOverViewModel.Action.TapIncreaseSuggestion(amount)) },
                onAmountChange = { text -> viewModel.perform(BudgetOverViewModel.Action.ChangeIncreaseAmount(text)) },
                onConfirm = { viewModel.perform(BudgetOverViewModel.Action.ConfirmIncrease) },
            )
        }
    }
}

@Composable
private fun ChoiceContent(
    target: BudgetOverViewModel.TargetItem,
    amountFormatter: AmountFormatter,
    onClose: () -> Unit,
    onReallocate: () -> Unit,
    onIncrease: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(ZeroTheme.colors.errorContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = ZeroTheme.colors.error,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.budget_over_choice_title, target.name),
                    style = TextStyle(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ZeroTheme.colors.onSurface,
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.budget_over_choice_subtitle,
                        amountFormatter.format(target.spent),
                        amountFormatter.format(target.budgeted),
                        amountFormatter.format(target.overage),
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ZeroTheme.colors.onSurfaceVariant,
                    ),
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.budget_over_close_cd),
                    modifier = Modifier.size(20.dp),
                    tint = ZeroTheme.colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OptionCard(
            title = stringResource(R.string.budget_over_choice_reallocate_title),
            subtitle = stringResource(R.string.budget_over_choice_reallocate_subtitle),
            icon = Icons.Filled.SwapHoriz,
            iconColor = ZeroTheme.colors.secondary,
            iconBackground = ZeroTheme.colors.secondaryContainer,
            onClick = onReallocate,
        )
        Spacer(Modifier.height(10.dp))
        OptionCard(
            title = stringResource(R.string.budget_over_choice_increase_title),
            subtitle = stringResource(R.string.budget_over_choice_increase_subtitle),
            icon = Icons.Filled.ArrowUpward,
            iconColor = ZeroTheme.colors.primary,
            iconBackground = ZeroTheme.colors.primaryContainerLight,
            onClick = onIncrease,
        )
    }
}

@Composable
private fun OptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconBackground, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconColor,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.onSurface,
                ),
            )
            Text(
                text = subtitle,
                modifier = Modifier.padding(top = 2.dp),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = ZeroTheme.colors.onSurfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun ReallocateContent(
    target: BudgetOverViewModel.TargetItem,
    sources: List<BudgetOverViewModel.SourceItem>,
    selectedSource: BudgetOverViewModel.SourceItem?,
    amountToMove: Amount,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onBack: () -> Unit,
    onSelectSource: (com.hluhovskyi.zero.common.Id.Known) -> Unit,
    onConfirm: () -> Unit,
) {
    SheetHeader(
        title = stringResource(R.string.budget_over_reallocate_header),
        onBack = onBack,
    )
    TargetContextPill(
        target = target,
        amountFormatter = amountFormatter,
        imageLoader = imageLoader,
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.budget_over_section_available).uppercase(),
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZeroTheme.colors.onSurfaceVariant,
                    letterSpacing = 0.8.sp,
                ),
            )
        }
        if (sources.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.budget_over_source_empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ZeroTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else {
            items(sources, key = { it.categoryId.value }) { source ->
                SourceCard(
                    source = source,
                    imageLoader = imageLoader,
                    amountFormatter = amountFormatter,
                    onClick = { onSelectSource(source.categoryId) },
                )
            }
        }
        if (selectedSource != null) {
            item {
                Spacer(Modifier.height(4.dp))
                PrimaryButton(
                    label = stringResource(
                        R.string.budget_over_reallocate_cta,
                        amountFormatter.format(amountToMove),
                        selectedSource.name,
                    ),
                    enabled = amountToMove > Amount.zero(),
                    onClick = onConfirm,
                    testTag = "Budget.over.reallocate.confirm",
                )
            }
        }
    }
}

@Composable
private fun TargetContextPill(
    target: BudgetOverViewModel.TargetItem,
    amountFormatter: AmountFormatter,
    imageLoader: ImageLoader,
) {
    val bg = target.colorScheme.background.value.toCompose()
    val primary = target.colorScheme.primary.value.toCompose()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(bg, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconView(
            colorScheme = target.colorScheme.toUi(),
            size = 36.dp,
            contentPadding = 8.dp,
        ) { tint ->
            imageLoader.View(
                modifier = Modifier.size(20.dp),
                image = target.icon,
                tint = tint,
            )
        }
        Column {
            Text(
                text = stringResource(R.string.budget_over_target_needs, target.name),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                ),
            )
            Text(
                text = stringResource(
                    R.string.budget_over_target_more,
                    amountFormatter.format(target.overage),
                ),
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = primary,
                ),
            )
        }
    }
}

@Composable
private fun SourceCard(
    source: BudgetOverViewModel.SourceItem,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onClick: () -> Unit,
) {
    val primary = source.colorScheme.primary.value.toCompose()
    val bg = source.colorScheme.background.value.toCompose()
    val shape = RoundedCornerShape(14.dp)
    val background = if (source.selected) bg else ZeroTheme.colors.surfaceContainerLowest
    val borderModifier = if (source.selected) {
        Modifier.border(2.dp, primary, shape)
    } else {
        Modifier.border(1.dp, ZeroTheme.colors.outlineVariant, shape)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, shape)
            .then(borderModifier)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag("Budget.over.source.${source.name}")
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryIconView(
                colorScheme = source.colorScheme.toUi(),
                size = 32.dp,
                contentPadding = 6.dp,
            ) { tint ->
                imageLoader.View(
                    modifier = Modifier.size(18.dp),
                    image = source.icon,
                    tint = tint,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.name,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZeroTheme.colors.onSurface,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.budget_over_source_remaining,
                        amountFormatter.format(source.remaining),
                    ),
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ZeroTheme.colors.onSurfaceVariant,
                    ),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(
                        if (source.coversIt) {
                            R.string.budget_over_source_covers
                        } else {
                            R.string.budget_over_source_partial
                        },
                    ),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (source.coversIt) ZeroTheme.colors.secondary else ZeroTheme.colors.onSurfaceVariant,
                    ),
                )
                Text(
                    text = stringResource(
                        R.string.budget_over_source_of,
                        amountFormatter.format(source.budgeted),
                    ),
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = ZeroTheme.colors.onSurfaceVariant,
                    ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(
            fraction = sourceFraction(source.spent, source.budgeted),
            color = primary.copy(alpha = 0.55f),
            trackColor = ZeroTheme.colors.surfaceContainer,
        )
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color, trackColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(trackColor, RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun IncreaseContent(
    target: BudgetOverViewModel.TargetItem,
    amountText: String,
    suggestions: List<BudgetOverViewModel.IncreaseSuggestion>,
    newBudgeted: Amount,
    imageLoader: ImageLoader,
    amountFormatter: AmountFormatter,
    onBack: () -> Unit,
    onSuggestion: (Amount) -> Unit,
    onAmountChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    SheetHeader(
        title = stringResource(R.string.budget_over_increase_header),
        onBack = onBack,
    )
    TargetContextPill(
        target = target,
        amountFormatter = amountFormatter,
        imageLoader = imageLoader,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.budget_over_section_suggestions).uppercase(),
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 0.8.sp,
            ),
        )
        if (suggestions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(
                        amount = amountFormatter.format(suggestion.amount),
                        newBudgeted = amountFormatter.format(suggestion.newBudgeted),
                        selected = suggestion.selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSuggestion(suggestion.amount) },
                    )
                }
            }
        }
        NewBudgetDisplay(
            label = stringResource(R.string.budget_over_increase_new_label),
            amount = amountFormatter.format(newBudgeted),
            highlight = amountText != "0",
        )
        NumPad(
            value = amountText,
            onChange = onAmountChange,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            label = stringResource(R.string.budget_over_increase_cta),
            enabled = amountText.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true,
            onClick = onConfirm,
            testTag = "Budget.over.increase.confirm",
        )
    }
}

@Composable
private fun SuggestionChip(
    amount: String,
    newBudgeted: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = if (selected) ZeroTheme.colors.primaryContainerLight else ZeroTheme.colors.surfaceContainerLow
    val border = if (selected) ZeroTheme.colors.primary else Color.Transparent
    Column(
        modifier = modifier
            .background(background, shape)
            .border(1.dp, border, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "+$amount",
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurface,
            ),
        )
        Text(
            text = "→ $newBudgeted",
            modifier = Modifier.padding(top = 2.dp),
            style = TextStyle(
                fontSize = 11.sp,
                color = ZeroTheme.colors.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun NewBudgetDisplay(label: String, amount: String, highlight: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurfaceVariant,
                letterSpacing = 1.sp,
            ),
        )
        Text(
            text = amount,
            modifier = if (highlight) Modifier else Modifier.alpha(0.45f),
            style = TextStyle(
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ZeroTheme.colors.primaryContainer,
            ),
        )
    }
}

@Composable
private fun SheetHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.budget_over_back_cd),
                modifier = Modifier.size(20.dp),
                tint = ZeroTheme.colors.onSurfaceVariant,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = ZeroTheme.colors.onSurface,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = if (enabled) ZeroTheme.colors.primaryContainer else ZeroTheme.colors.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, shape)
            .clip(shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ZeroTheme.colors.surface,
            ),
        )
    }
}

private fun sourceFraction(spent: Amount, budgeted: Amount): Float {
    if (budgeted <= Amount.zero()) return 0f
    val raw = spent.value.toDouble() / budgeted.value.toDouble()
    return raw.toFloat().coerceIn(0f, 1f)
}
