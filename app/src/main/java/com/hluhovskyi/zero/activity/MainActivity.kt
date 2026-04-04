package com.hluhovskyi.zero.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.logging
import com.hluhovskyi.zero.requireApplicationComponent

class MainActivity : ComponentActivity() {

    private val activityComponent: AttachableViewComponent by lazy {
        val applicationComponent = application.requireApplicationComponent()

        applicationComponent.activityComponentBuilder
            .logging(applicationComponent.logger)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            activityComponent.AttachWithView()
        }
    }
}
