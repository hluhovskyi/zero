package com.hluhovskyi.zero.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.hluhovskyi.zero.ui.ZeroFab
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

    if (state.submitted) {
        SentView(onDone = { viewModel.perform(FeedbackViewModel.Action.Done) })
        return
    }

    val colors = ZeroTheme.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
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
                focusRequester = focusRequester,
            )

            val errorMessage = state.errorMessage
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = TextStyle(fontSize = 12.sp, color = colors.error),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        val canSend = state.description.isNotBlank() && !state.isSubmitting
        ZeroFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            onClick = { if (canSend) viewModel.perform(FeedbackViewModel.Action.Submit) },
            icon = Icons.Filled.Send,
            contentDescription = stringResource(R.string.feedback_submit),
            expanded = true,
            text = stringResource(R.string.feedback_submit),
        )
    }
}

@Composable
private fun SentView(onDone: () -> Unit) {
    val colors = ZeroTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(colors.secondaryContainer, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = stringResource(R.string.feedback_sent_title),
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = colors.primary),
            modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
        )
        Text(
            text = stringResource(R.string.feedback_sent_body),
            style = TextStyle(
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.primaryContainer, shape = RoundedCornerShape(16.dp))
                .clickable { onDone() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.feedback_done),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onPrimary),
            )
        }
        Spacer(Modifier.height(32.dp))
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
    focusRequester: FocusRequester,
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
                .height(132.dp)
                .focusRequester(focusRequester),
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
