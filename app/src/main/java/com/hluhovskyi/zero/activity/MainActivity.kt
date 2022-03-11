package com.hluhovskyi.zero.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.requireApplicationComponent

class MainActivity : ComponentActivity() {

    private val activityComponent: ActivityComponent by lazy {
        application.requireApplicationComponent()
            .activityComponentBuilder
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            activityComponent.AttachWithView()
        }
    }
}
