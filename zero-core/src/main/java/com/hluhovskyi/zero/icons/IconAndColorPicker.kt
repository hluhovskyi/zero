package com.hluhovskyi.zero.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.ui.SearchBar
import com.hluhovskyi.zero.ui.UiColorScheme
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Surface
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLow

private const val GRID_COLUMNS = 6

@Composable
fun IconAndColorPicker(
    sections: List<IconPickerSection>,
    colorSchemes: List<ColorScheme>,
    selectedIcon: Icon?,
    selectedColorScheme: ColorScheme?,
    imageLoader: ImageLoader,
    onIconSelected: (Icon) -> Unit,
    onColorSchemeSelected: (ColorScheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }

    val filteredSections = remember(sections, query) {
        if (query.isBlank()) {
            sections
        } else {
            sections.mapNotNull { section ->
                val matching = section.icons.filter {
                    it.image.description.contains(query, ignoreCase = true)
                }
                if (matching.isEmpty()) null else section.copy(icons = matching)
            }
        }
    }

    val activeScheme = selectedColorScheme?.toUi() ?: UiColorScheme.default()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .background(Surface)
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search icons…",
            )
            Spacer(modifier = Modifier.height(14.dp))
            ColorSchemesRow(
                colorSchemes = colorSchemes,
                selected = selectedColorScheme,
                onColorSchemeSelected = onColorSchemeSelected,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filteredSections.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "No icons match \"$query\"",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        color = OnSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                filteredSections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionTitle(title = section.category.name)
                    }
                    items(section.icons, key = { it.id.value }) { icon ->
                        IconPickerCell(
                            icon = icon,
                            isSelected = icon == selectedIcon,
                            colorScheme = activeScheme,
                            imageLoader = imageLoader,
                            onClick = { onIconSelected(icon) },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ColorSchemesRow(
    colorSchemes: List<ColorScheme>,
    selected: ColorScheme?,
    onColorSchemeSelected: (ColorScheme) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        colorSchemes.forEach { scheme ->
            ColorSwatch(
                scheme = scheme,
                isSelected = scheme == selected,
                onClick = { onColorSchemeSelected(scheme) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    scheme: ColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val uiScheme = scheme.toUi()
    Box(
        modifier = Modifier
            .size(32.dp)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, uiScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(uiScheme.primary, CircleShape),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(top = 16.dp, bottom = 10.dp, start = 2.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurfaceVariant,
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun IconPickerCell(
    icon: Icon,
    isSelected: Boolean,
    colorScheme: UiColorScheme,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                color = if (isSelected) colorScheme.background else SurfaceContainerLow,
                shape = shape,
            )
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        imageLoader.View(
            modifier = Modifier.size(24.dp),
            image = icon.image,
            tint = if (isSelected) colorScheme.primary else OnSurfaceVariant,
        )
    }
}
