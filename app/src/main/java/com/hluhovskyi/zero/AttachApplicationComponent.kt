package com.hluhovskyi.zero

import com.hluhovskyi.zero.activity.CurrentActivityTracker
import com.hluhovskyi.zero.backup.BackupNotificationPresenter
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Closeables
import java.io.Closeable

internal class AttachApplicationComponent(
    private val crashComponent: CrashComponent,
    private val currentActivityTracker: CurrentActivityTracker,
    private val backupNotificationPresenter: BackupNotificationPresenter,
) : Attachable {

    override fun attach(): Closeable = Closeables.merge(
        crashComponent.attachable.attach(),
        currentActivityTracker.attach(),
        backupNotificationPresenter.attach(),
    )
}
