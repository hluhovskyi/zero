package com.hluhovskyi.zero.imports.categoriesreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportCategory
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.OutlineVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Secondary
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

private const val TOTAL_STEPS = 4
private const val CURRENT_STEP = 1

internal class CategoriesReviewViewProvider(
    private val viewModel: CategoriesReviewViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        CategoriesReviewView(viewModel = viewModel, imageLoader = imageLoader)
    }
}

@Composable
private fun CategoriesReviewView(viewModel: CategoriesReviewViewModel, imageLoader: ImageLoader) {
    val state by viewModel.state.collectAsState(initial = CategoriesReviewViewModel.State())
    val categoryCount = state.categories.size
    val selectedCount = categoryCount - state.excludedIds.size

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Header ──────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.perform(CategoriesReviewViewModel.Action.Back) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryContainer,
                )
            }
            Text(
                text = "Review Categories",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryContainer,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = "${CURRENT_STEP + 1}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        // ── Step progress bar ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 0.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(TOTAL_STEPS - 1) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < CURRENT_STEP) PrimaryContainer else OutlineVariant),
                )
            }
        }

        // ── List ─────────────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = "$selectedCount CATEGORIES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.08.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, start = 4.dp),
                )
            }
            items(state.categories, key = { it.id.value }) { category ->
                val isSelected = category.id !in state.excludedIds
                CategoryRow(
                    category = category,
                    isSelected = isSelected,
                    imageLoader = imageLoader,
                    onClick = { viewModel.perform(CategoriesReviewViewModel.Action.ToggleCategory(category.id)) },
                )
            }
            item { Box(modifier = Modifier.height(8.dp)) }
        }

        // ── CTA button ────────────────────────────────────────────────────────────
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).padding(bottom = 16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryContainer)
                    .clickable { viewModel.perform(CategoriesReviewViewModel.Action.Next) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Continue",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: ImportCategory,
    isSelected: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconView(
            colorScheme = category.colorScheme.toUi(),
            size = 36.dp,
            contentPadding = 8.dp,
            isSelected = isSelected,
        ) { iconTint ->
            imageLoader.View(
                image = category.icon,
                modifier = Modifier.fillMaxSize(),
                tint = if (iconTint == Color.Unspecified) null else iconTint,
            )
        }
        Text(
            text = category.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Secondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
