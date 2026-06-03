package com.hluhovskyi.zero.activity.screens.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.View
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class BottomBarViewProvider(
    private val viewModel: BottomBarViewModel,
    private val imageLoader: ImageLoader,
) : ViewProvider {

    @Composable
    override fun View() {
        BottomBarView(
            viewModel = viewModel,
            imageLoader = imageLoader,
        )
    }
}

@Composable
internal fun BottomBarView(
    viewModel: BottomBarViewModel,
    imageLoader: ImageLoader,
) {
    val state by viewModel.state.collectAsState(initial = BottomBarViewModel.State())
    if (state.items.isNotEmpty()) {
        NavigationBar(
            modifier = Modifier.height(72.dp),
            containerColor = ZeroTheme.colors.surfaceContainerLowest,
            tonalElevation = 0.dp,
        ) {
            val overBudgetDescription = stringResource(R.string.bottom_bar_over_budget_description)
            state.items.forEach { item ->
                NavigationBarItem(
                    // M3 NavigationBarItem clears the icon slot's semantics (alwaysShowLabel), which
                    // drops the over-budget dot's description from the merged tree — set it on the item.
                    modifier = if (item.hasAlert) {
                        Modifier.semantics { contentDescription = overBudgetDescription }
                    } else {
                        Modifier
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ZeroTheme.colors.primary,
                        unselectedIconColor = ZeroTheme.colors.outline,
                        selectedTextColor = ZeroTheme.colors.primary,
                        unselectedTextColor = ZeroTheme.colors.outline,
                        indicatorColor = Color.Transparent,
                    ),
                    alwaysShowLabel = true,
                    selected = item.selected,
                    onClick = { viewModel.perform(BottomBarViewModel.Action.SelectItem(item)) },
                    icon = {
                        val iconTint = if (item.selected) ZeroTheme.colors.primary else ZeroTheme.colors.outline
                        val pillColor = if (item.selected) ZeroTheme.colors.selectedPill else Color.Transparent
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .requiredSize(width = 56.dp, height = 32.dp)
                                    .background(pillColor, RoundedCornerShape(50)),
                            )
                            Box {
                                imageLoader.View(
                                    uri = item.iconUri,
                                    contentDescription = stringResource(item.id.iconDescriptionRes()),
                                    modifier = Modifier.sizeIn(maxHeight = 24.dp),
                                    tint = iconTint,
                                )
                                if (item.hasAlert) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 5.dp, y = (-5).dp)
                                            .size(13.dp)
                                            .background(ZeroTheme.colors.surfaceContainerLowest, CircleShape)
                                            .padding(2.dp)
                                            .background(ZeroTheme.colors.error, CircleShape),
                                    )
                                }
                            }
                        }
                    },
                    label = { Text(text = stringResource(item.id.labelRes())) },
                )
            }
        }
    }
}

private fun Id.Known.labelRes(): Int = when (this) {
    BottomBarViewModel.HomeId -> R.string.bottom_bar_home
    BottomBarViewModel.AccountsId -> R.string.bottom_bar_accounts
    BottomBarViewModel.BudgetId -> R.string.bottom_bar_budget
    BottomBarViewModel.CategoriesId -> R.string.bottom_bar_categories
    BottomBarViewModel.SettingsId -> R.string.bottom_bar_settings
    else -> error("Unknown bottom bar item id: $value")
}

private fun Id.Known.iconDescriptionRes(): Int = when (this) {
    BottomBarViewModel.HomeId -> R.string.bottom_bar_home_icon_description
    BottomBarViewModel.AccountsId -> R.string.bottom_bar_accounts_icon_description
    BottomBarViewModel.BudgetId -> R.string.bottom_bar_budget_icon_description
    BottomBarViewModel.CategoriesId -> R.string.bottom_bar_categories_icon_description
    BottomBarViewModel.SettingsId -> R.string.bottom_bar_settings_icon_description
    else -> error("Unknown bottom bar item id: $value")
}
