package com.hluhovskyi.zero.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.ui.theme.OnSurfaceVariant
import com.hluhovskyi.zero.ui.theme.PrimaryContainer
import com.hluhovskyi.zero.ui.theme.Secondary
import com.hluhovskyi.zero.ui.theme.SurfaceContainer

private val MergeBackground = Color(0xFFE8EEFF)
private val NewBackground = Color(0xFFE8F5E9)

private data class StrategyVisual(
    val background: Color,
    val foreground: Color,
    val label: String,
    val description: String,
)

@Composable
private fun visuals(): Map<ResolveStrategy, StrategyVisual> = mapOf(
    ResolveStrategy.Merge to StrategyVisual(
        background = MergeBackground,
        foreground = PrimaryContainer,
        label = stringResource(R.string.import_resolve_strategy_merge),
        description = stringResource(R.string.import_resolve_strategy_merge_desc),
    ),
    ResolveStrategy.New to StrategyVisual(
        background = NewBackground,
        foreground = Secondary,
        label = stringResource(R.string.import_resolve_strategy_new),
        description = stringResource(R.string.import_resolve_strategy_new_desc),
    ),
    ResolveStrategy.Skip to StrategyVisual(
        background = SurfaceContainer,
        foreground = OnSurfaceVariant,
        label = stringResource(R.string.import_resolve_strategy_skip),
        description = stringResource(R.string.import_resolve_strategy_skip_desc),
    ),
)

@Composable
fun ImportStrategyChip(
    selected: ResolveStrategy,
    options: List<ResolveStrategy>,
    onChange: (ResolveStrategy) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val visualMap = visuals()
    val visual = visualMap.getValue(selected)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(visual.background)
                .clickable { open = true }
                .padding(start = 8.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(visual.foreground),
            )
            Text(
                text = visual.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = visual.foreground,
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = visual.foreground.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            options.forEach { option ->
                val optionVisual = visualMap.getValue(option)
                val isSelected = option == selected
                Row(
                    modifier = Modifier
                        .background(if (isSelected) optionVisual.background else Color.Transparent)
                        .clickable {
                            onChange(option)
                            open = false
                        }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(optionVisual.foreground),
                    )
                    Column(modifier = Modifier.padding(end = 24.dp)) {
                        Text(
                            text = optionVisual.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = optionVisual.foreground,
                        )
                        Text(
                            text = optionVisual.description,
                            fontSize = 11.sp,
                            color = OnSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = optionVisual.foreground,
                            modifier = Modifier.size(14.dp),
                        )
                    } else {
                        Box(modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
