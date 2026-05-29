package com.hluhovskyi.zero.backup

import android.content.Context
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.notifications.Notifier
import dagger.Provides
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
internal annotation class BackupApplicationScope

@BackupApplicationScope
@dagger.Component(
    modules = [BackupApplicationComponent.Module::class],
    dependencies = [BackupApplicationComponent.Dependencies::class],
)
internal abstract class BackupApplicationComponent {

    abstract val backupNotificationPresenter: BackupNotificationPresenter

    interface Dependencies {
        val context: Context
        val backupUseCase: BackupUseCase
        val notifier: Notifier
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBackupApplicationComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BackupApplicationComponent> {
        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BackupApplicationScope
        fun backupNotificationPresenter(
            context: Context,
            backupUseCase: BackupUseCase,
            notifier: Notifier,
        ): BackupNotificationPresenter = BackupNotificationPresenter(
            context = context,
            backupUseCase = backupUseCase,
            notifier = notifier,
        )
    }
}
