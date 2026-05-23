package com.hluhovskyi.zero.categories.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.SegmentedToggle
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.ZeroFab
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class CategoriesEditViewProvider(
    private val viewModel: CategoryEditViewModel,
    private val onDiscard: OnDiscardHandler,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoryEditView(
            viewModel = viewModel,
            onDiscard = onDiscard,
            imageLoader = imageLoader,
        )
    }
}

@Composable
private fun CategoryEditView(
    viewModel: CategoryEditViewModel,
    onDiscard: OnDiscardHandler,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = CategoryEditViewModel.State())
    val expenseLabel = stringResource(R.string.transaction_type_expense)
    val incomeLabel = stringResource(R.string.transaction_type_income)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = Modifier.fillMaxSize().focusTarget()) {
        Column {
            ModalHeader(
                title = stringResource(R.string.category_edit_title),
                onClose = { onDiscard.onDiscard() },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SegmentedToggle(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    items = listOf(CategoryType.EXPENSE, CategoryType.INCOME),
                    selectedItem = state.type,
                    onItemSelected = { viewModel.perform(CategoryEditViewModel.Action.SelectType(it)) },
                    labelMapping = { if (it == CategoryType.EXPENSE) expenseLabel else incomeLabel },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CategoryIconTile(
                        modifier = Modifier.fillMaxHeight(),
                        colorScheme = state.colorScheme.toUi(),
                        imageLoader = imageLoader,
                        icon = state.icon,
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.perform(CategoryEditViewModel.Action.SelectIcon)
                        },
                    )
                    NameFormCard(
                        modifier = Modifier.weight(1f),
                        value = state.name,
                        focusRequester = focusRequester,
                        onValueChange = { viewModel.perform(CategoryEditViewModel.Action.ChangeName(it)) },
                    )
                }

                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        ZeroFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            onClick = { viewModel.perform(CategoryEditViewModel.Action.Save) },
            icon = Icons.Filled.Check,
            contentDescription = stringResource(R.string.category_edit_save_description),
            expanded = true,
            text = stringResource(R.string.category_edit_save),
        )
    }
}

@Composable
private fun CategoryIconTile(
    modifier: Modifier = Modifier,
    colorScheme: UiColorScheme,
    imageLoader: ImageLoader,
    icon: com.hluhovskyi.zero.common.Image,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(colorScheme.background, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        imageLoader.View(
            image = icon,
            modifier = Modifier
                .align(Alignment.Center)
                .size(26.dp),
            tint = colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(14.dp),
            tint = colorScheme.primary,
        )
    }
}

@Composable
private fun NameFormCard(
    modifier: Modifier = Modifier,
    value: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ZeroTheme.colors.surfaceContainerLow, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.category_edit_name_label).uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ZeroTheme.colors.onSurfaceVariant,
            letterSpacing = 1.2.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = ZeroTheme.colors.onSurface,
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.category_edit_name_placeholder),
                        fontSize = 16.sp,
                        color = ZeroTheme.colors.onSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}
