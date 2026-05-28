package com.hluhovskyi.zero.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.hluhovskyi.zero.backup.EXTRA_OPEN_SETTINGS_BACKUP
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.logging
import com.hluhovskyi.zero.requireApplicationComponent

class MainActivity : FragmentActivity() {

    private val activityComponent: AttachableViewComponent by lazy {
        val applicationComponent = application.requireApplicationComponent()
        applicationComponent.activityComponentBuilder
            .activity(this)
            .logging(applicationComponent.logger)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleBackupDeepLink(intent)
        setContent {
            activityComponent.AttachWithView()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleBackupDeepLink(intent)
    }

    private fun handleBackupDeepLink(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS_BACKUP, false) != true) return
        application.requireApplicationComponent().backupDeepLinkSignal.request()
        intent.removeExtra(EXTRA_OPEN_SETTINGS_BACKUP)
    }
}
