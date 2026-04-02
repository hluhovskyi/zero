package com.hluhovskyi.zero.transactions.edit.expense

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ColorValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.transactions.edit.TransactionEditAccount
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategory
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrency
import com.hluhovskyi.zero.transactions.edit.common.TransactionEditRateTextField
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import android.app.DatePickerDialog
import androidx.compose.foundation.layout.widthIn
import com.hluhovskyi.zero.colors.Color as DomainColor

internal class TransactionEditExpenseViewProvider(
    private val viewModel: TransactionEditExpenseViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        TransactionEditExpenseView(
            viewModel = viewModel,
            imageLoader = imageLoader
        )
    }
}

@Composable
private fun TransactionEditExpenseView(
    viewModel: TransactionEditExpenseViewModel,
    imageLoader: ImageLoader
) {
    val state by viewModel.state.collectAsState(initial = TransactionEditExpenseViewModel.State())
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        AmountDisplay(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 40.dp),
            amount = state.amount,
            currencySymbol = state.selectedCurrency?.currencySymbol ?: "",
            focusRequester = focusRequester,
            onAmountChange = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeAmount(it))
            },
            currencies = state.currencies,
            onCurrencySelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCurrency(it))
            }
        )

        CategoryScrollRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            imageLoader = imageLoader,
            categories = state.categories,
            selectedCategory = state.selectedCategory,
            onCategorySelected = {
                viewModel.perform(TransactionEditExpenseViewModel.Action.SelectCategory(it))
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DatePickerCard(
                modifier = Modifier.weight(1f),
                label = "Date",
                date = state.date,
                onDateSelected = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeDate(it))
                }
            )
            SelectorCard(
                modifier = Modifier.weight(1f),
                label = "Account",
                value = state.selectedAccount?.name ?: "",
                items = state.accounts,
                nameMapping = { it.name },
                onItemSelected = {
                    viewModel.perform(TransactionEditExpenseViewModel.Action.SelectAccount(it))
                }
            )
        }

        AnimatedVisibility(visible = state.showRate) {
            TransactionEditRateTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                rate = state.rate,
                onValueChange = { rate ->
                    viewModel.perform(TransactionEditExpenseViewModel.Action.ChangeRate(rate))
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun AmountDisplay(
    modifier: Modifier = Modifier,
    amount: String,
    currencySymbol: String,
    focusRequester: FocusRequester,
    onAmountChange: (String) -> Unit,
    currencies: List<TransactionEditCurrency>,
    onCurrencySelected: (TransactionEditCurrency) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "AMOUNT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 3.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.clickable { expanded = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currencySymbol,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(top = 4.dp),
                            tint = OnSurfaceVariant,
                        )
                    }

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        currencies.forEach { currency ->
                            DropdownMenuItem(
                                onClick = {
                                    onCurrencySelected(currency)
                                    expanded = false
                                }
                            ) {
                                Text(text = "${currency.currencySymbol} - ${currency.name}")
                            }
                        }
                    }
                }

                BasicTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier
                        .sizeIn(minWidth = 40.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Start,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(MaterialTheme.colors.primary),
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
private fun CategoryScrollRow(
    modifier: Modifier = Modifier,
    imageLoader: ImageLoader,
    categories: List<TransactionEditCategory>,
    selectedCategory: TransactionEditCategory?,
    onCategorySelected: (TransactionEditCategory) -> Unit
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(categories, key = { it.id.value }) { category ->
            val isSelected = category.id == selectedCategory?.id
            CategoryItem(
                imageLoader = imageLoader,
                category = category,
                isSelected = isSelected,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun CategoryItem(
    imageLoader: ImageLoader,
    category: TransactionEditCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryIconView(
            colorScheme = category.colorScheme,
            size = 48.dp,
            contentPadding = 12.dp,
        ) { iconTint ->
            imageLoader.View(
                modifier = Modifier.sizeIn(maxHeight = 24.dp, maxWidth = 24.dp),
                image = category.icon,
                tint = if (isSelected) {
                    DomainColor(Id("white"), ColorValue(0xFFFFFFFF.toULong()))
                } else {
                    iconTint
                },
            )
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = category.name,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) MaterialTheme.colors.primary else OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun <T> SelectorCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    items: List<T>,
    nameMapping: (T) -> String,
    onItemSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
                .clickable { expanded = true }
                .padding(16.dp)
        ) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = OnSurfaceVariant,
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                ) {
                    Text(text = nameMapping(item))
                }
            }
        }
    }
}

@Composable
private fun DatePickerCard(
    modifier: Modifier = Modifier,
    label: String,
    date: LocalDateTime,
    onDateSelected: (LocalDateTime) -> Unit
) {
    val context = LocalContext.current
    val formattedDate = remember(date) {
        date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    }

    Column(
        modifier = modifier
            .background(SurfaceContainerLow, RoundedCornerShape(16.dp))
            .clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateSelected(LocalDateTime.of(year, month + 1, dayOfMonth, 0, 0))
                    },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth
                ).show()
            }
            .padding(16.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 1.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = formattedDate,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = OnSurfaceVariant,
            )
        }
    }
}
