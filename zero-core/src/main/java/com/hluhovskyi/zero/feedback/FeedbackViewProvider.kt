package com.hluhovskyi.zero.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

@Composable
private fun FeedbackView(viewModel: FeedbackViewModel) {
    val state by viewModel.state.collectAsState(initial = FeedbackViewModel.State())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.feedback_title),
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = ZeroTheme.colors.onSurface,
            ),
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = {
                viewModel.perform(FeedbackViewModel.Action.UpdateDescription(it))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.feedback_description_hint)) },
            minLines = 4,
            maxLines = 8,
            enabled = !state.isSubmitting,
        )

        val errorMessage = state.errorMessage
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = TextStyle(fontSize = 12.sp, color = ZeroTheme.colors.error),
            )
        }

        Text(
            text = state.deviceInfoPreview,
            style = TextStyle(fontSize = 12.sp, color = ZeroTheme.colors.onSurfaceVariant),
        )

        Button(
            onClick = { viewModel.perform(FeedbackViewModel.Action.Submit) },
            enabled = state.description.isNotBlank() && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isSubmitting) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            } else {
                Text(stringResource(R.string.feedback_submit))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
