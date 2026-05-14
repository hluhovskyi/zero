package com.hluhovskyi.zero.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Segmented toggle + horizontal pager wired together for the common "swipe between tabs" pattern.
 *
 * - Tapping a segment animates the pager to that page.
 * - Swiping the pager updates the selected item via [onItemSelected].
 * - The segment indicator tracks the pager offset in real time, so it slides under the gesture
 *   instead of snapping at the page-change threshold.
 *
 * Compose has no built-in tabs-plus-pager widget — `TabRow`/`HorizontalPager` still requires manual
 * `LaunchedEffect` wiring. This component encapsulates that pattern for the project's [SegmentedToggle].
 *
 * @param toggleModifier applied to the [SegmentedToggle] (padding, width, etc.).
 * @param pageContent renders the body for each item; receives the item itself, not the index.
 */
@Composable
fun <T> SwipeableSegmentedTabs(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelMapping: (T) -> String,
    modifier: Modifier = Modifier,
    toggleModifier: Modifier = Modifier,
    pageContent: @Composable (T) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = items.indexOf(selectedItem).coerceAtLeast(0),
    ) { items.size }

    LaunchedEffect(selectedItem) {
        val target = items.indexOf(selectedItem)
        if (target >= 0 && pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val item = items.getOrNull(page) ?: return@collect
                if (item != selectedItem) {
                    onItemSelected(item)
                }
            }
    }

    Column(modifier = modifier) {
        SegmentedToggle(
            modifier = toggleModifier,
            items = items,
            selectedItem = selectedItem,
            onItemSelected = onItemSelected,
            labelMapping = labelMapping,
            selectedFraction = pagerState.currentPage + pagerState.currentPageOffsetFraction,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            pageContent(items[page])
        }
    }
}
