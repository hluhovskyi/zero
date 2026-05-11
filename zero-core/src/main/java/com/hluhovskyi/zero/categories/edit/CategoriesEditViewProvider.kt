package com.hluhovskyi.zero.categories.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.icons.IconAndColorPicker
import com.hluhovskyi.zero.ui.DragHandle
import com.hluhovskyi.zero.ui.ModalHeader
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Surface
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            DragHandle()
            ModalHeader(
                title = "Add Category",
                onClose = { onDiscard.onDiscard() },
            )
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                        onClick = { viewModel.perform(CategoryEditViewModel.Action.TogglePicker) },
                    )
                    NameFormCard(
                        modifier = Modifier.weight(1f),
                        value = state.name,
                        onValueChange = { viewModel.perform(CategoryEditViewModel.Action.ChangeName(it)) },
                    )
                }
                SaveButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 32.dp),
                    onClick = { viewModel.perform(CategoryEditViewModel.Action.Save) },
                )
            }
        }

        if (state.pickerVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable { viewModel.perform(CategoryEditViewModel.Action.TogglePicker) },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.78f)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Surface),
                ) {
                    Column {
                        DragHandle()
                        IconAndColorPicker(
                            sections = state.iconSections,
                            colorSchemes = state.colorSchemes,
                            selectedIcon = state.selectedIcon,
                            selectedColorScheme = state.colorScheme.takeIf { it.swatch.id.value.isNotEmpty() },
                            imageLoader = imageLoader,
                            onIconSelected = { icon ->
                                viewModel.perform(CategoryEditViewModel.Action.PickIcon(icon))
                            },
                            onColorSchemeSelected = { scheme ->
                                viewModel.perform(CategoryEditViewModel.Action.PickColorScheme(scheme))
                            },
                        )
                    }
                }
            }
        }
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
            .size(64.dp)
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
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "CATEGORY NAME",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceVariant,
            letterSpacing = 1.2.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = "e.g. Groceries",
                        fontSize = 16.sp,
                        color = OnSurfaceVariant,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun SaveButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(PrimaryContainer, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Save Category",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
