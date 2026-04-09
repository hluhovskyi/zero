package com.hluhovskyi.zero.activity.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable

@Composable
fun AccountsScreen(
    component: Buildable<out AttachableViewComponent>,
    onAccountEdit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        component.AttachWithView()
        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 32.dp),
            icon = { Icon(Icons.Filled.Add, "Add account") },
            text = { Text("Add account") },
            onClick = onAccountEdit,
            elevation = FloatingActionButtonDefaults.elevation(8.dp),
        )
    }
}
