package com.hluhovskyi.zero.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.ui.theme.ZeroTheme

internal class FeedbackViewProvider(
    private val viewModel: FeedbackViewModel,
) : ViewProvider {

    @Composable
    override fun View() {
        FeedbackView(viewModel = viewModel)
    }
}

private const val MAX_CHARS = 1000
private const val WARN_CHARS = 900

private data class TypeOption(
    val type: FeedbackType,
    val iconRes: Int,
    val labelRes: Int,
    val hintRes: Int,
)

private val TYPES = listOf(
    TypeOption(FeedbackType.Bug, R.drawable.ic_feedback_bug_24, R.string.feedback_type_bug, R.string.feedback_hint_bug),
    TypeOption(FeedbackType.Idea, R.drawable.ic_feedback_idea_24, R.string.feedback_type_idea, R.string.feedback_hint_idea),
    TypeOption(FeedbackType.Other, R.drawable.ic_feedback_other_24, R.string.feedback_type_other, R.string.feedback_hint_other),
)

@Composable
private fun FeedbackView(viewModel: FeedbackViewModel) {
    val state by viewModel.state.collectAsState(initial = FeedbackViewModel.State())
    val colors = ZeroTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
    ) {
        Header(onClose = { viewModel.perform(FeedbackViewModel.Action.Close) })

        Text(
            text = stringResource(R.string.feedback_eyebrow),
            style = TextStyle(fontSize = 13.sp, color = colors.onSurfaceVariant, lineHeight = 19.sp),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        TypePillRow(
            selected = state.type,
            enabled = !state.isSubmitting,
            onSelect = { viewModel.perform(FeedbackViewModel.Action.SelectType(it)) },
        )

        Spacer(Modifier.height(16.dp))

        DescriptionCard(
            value = state.description,
            type = state.type,
            enabled = !state.isSubmitting,
            onChange = { viewModel.perform(FeedbackViewModel.Action.UpdateDescription(it)) },
        )

        val errorMessage = state.errorMessage
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = TextStyle(fontSize = 12.sp, color = colors.error),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SendButton(
            isSubmitting = state.isSubmitting,
            enabled = state.description.isNotBlank() && !state.isSubmitting,
            onClick = { viewModel.perform(FeedbackViewModel.Action.Submit) },
        )

        Text(
            text = stringResource(R.string.feedback_privacy_footnote),
            style = TextStyle(fontSize = 11.sp, color = colors.outline, lineHeight = 16.sp, textAlign = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    val colors = ZeroTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_close_24),
                contentDescription = stringResource(R.string.feedback_close),
                tint = colors.primaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.feedback_title),
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryContainer,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun TypePillRow(selected: FeedbackType, enabled: Boolean, onSelect: (FeedbackType) -> Unit) {
    val colors = ZeroTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        TYPES.forEach { option ->
            val isSelected = option.type == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = if (isSelected) colors.surfaceContainerLowest else colors.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) colors.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable(enabled = enabled) { onSelect(option.type) }
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    painter = painterResource(option.iconRes),
                    contentDescription = null,
                    tint = if (isSelected) colors.primaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(option.labelRes),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) colors.primaryContainer else colors.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DescriptionCard(
    value: String,
    type: FeedbackType,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val colors = ZeroTheme.colors
    val hintRes = TYPES.first { it.type == type }.hintRes
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerLow, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.feedback_what_happened),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
                letterSpacing = 1.2.sp,
            ),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= MAX_CHARS) onChange(it) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            textStyle = TextStyle(fontSize = 15.sp, color = colors.onSurface, lineHeight = 22.sp),
            cursorBrush = SolidColor(colors.primaryContainer),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(hintRes),
                        style = TextStyle(fontSize = 15.sp, color = colors.outline, lineHeight = 22.sp),
                    )
                }
                innerTextField()
            },
        )
        Text(
            text = stringResource(R.string.feedback_char_counter, value.length),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (value.length > WARN_CHARS) colors.error else colors.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SendButton(isSubmitting: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = ZeroTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(
                color = if (enabled || isSubmitting) colors.primaryContainer else colors.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = colors.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = stringResource(R.string.feedback_submit),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) colors.onPrimary else colors.outline,
                ),
            )
        }
    }
}
