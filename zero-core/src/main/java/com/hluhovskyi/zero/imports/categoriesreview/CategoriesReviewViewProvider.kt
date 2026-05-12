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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportCategory
import com.hluhovskyi.zero.imports.ImportStrategyChip
import com.hluhovskyi.zero.imports.ResolveStrategy
import com.hluhovskyi.zero.ui.CategoryIconView
import com.hluhovskyi.zero.ui.ImportStepHeader
import com.hluhovskyi.zero.ui.common.toUi
import com.hluhovskyi.zero.ui.theme.OnSurface
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.Outline
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.SurfaceContainerLowest

private const val TOTAL_STEPS = 4
private const val CURRENT_STEP = 1

private val ExistsBadgeBackground = Color(0xFFE8EEFF)

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

    Column(modifier = Modifier.fillMaxSize()) {
        ImportStepHeader(
            title = stringResource(R.string.import_categories_review_title),
            step = CURRENT_STEP,
            totalSteps = TOTAL_STEPS,
            onBack = { viewModel.perform(CategoriesReviewViewModel.Action.Back) },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.import_categories_review_info, state.categories.size),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.08.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp, start = 4.dp),
                )
            }
            items(state.categories, key = { it.id.value }) { category ->
                val strategy = state.strategies[category.id]
                    ?: if (category.existingId != null) ResolveStrategy.Merge else ResolveStrategy.New
                CategoryRow(
                    category = category,
                    strategy = strategy,
                    imageLoader = imageLoader,
                    onChange = { viewModel.perform(CategoriesReviewViewModel.Action.SetStrategy(category.id, it)) },
                )
            }
            item { Box(modifier = Modifier.height(8.dp)) }
        }

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
                    text = stringResource(R.string.import_categories_review_continue),
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
    strategy: ResolveStrategy,
    imageLoader: ImageLoader,
    onChange: (ResolveStrategy) -> Unit,
) {
    val isSkipped = strategy == ResolveStrategy.Skip
    val options = if (category.existingId != null) {
        listOf(ResolveStrategy.Merge, ResolveStrategy.New, ResolveStrategy.Skip)
    } else {
        listOf(ResolveStrategy.New, ResolveStrategy.Skip)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLowest)
            .alpha(if (isSkipped) 0.4f else 1f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryIconView(
            colorScheme = category.colorScheme.toUi(),
            size = 40.dp,
            contentPadding = 9.dp,
            isSelected = !isSkipped,
        ) { iconTint ->
            imageLoader.View(
                image = category.icon,
                modifier = Modifier.fillMaxSize(),
                tint = if (iconTint == Color.Unspecified) null else iconTint,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = category.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (category.existingId != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ExistsBadgeBackground)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.import_resolve_exists_badge).uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryContainer,
                            letterSpacing = 0.06.sp,
                        )
                    }
                }
            }
            Text(
                text = if (isSkipped) {
                    stringResource(R.string.import_resolve_wont_be_imported)
                } else {
                    pluralStringResource(
                        R.plurals.import_categories_review_tx_count,
                        category.transactionCount,
                        category.transactionCount,
                    )
                },
                fontSize = 11.sp,
                color = if (isSkipped) Outline else OnSurfaceVariant,
            )
        }
        ImportStrategyChip(
            selected = strategy,
            options = options,
            onChange = onChange,
        )
    }
}
