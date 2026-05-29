package com.hluhovskyi.zero.scheduling

import com.hluhovskyi.zero.backup.BackupScheduler
import com.hluhovskyi.zero.backup.BackupUseCase
import com.hluhovskyi.zero.backup.DefaultBackupScheduler
import com.hluhovskyi.zero.backup.PerformBackup
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.users.CurrentUserRepository
import dagger.Provides
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
internal annotation class WorkSchedulerScope

@WorkSchedulerScope
@dagger.Component(
    modules = [WorkSchedulerComponent.Module::class],
    dependencies = [WorkSchedulerComponent.Dependencies::class],
)
abstract class WorkSchedulerComponent internal constructor() {

    abstract val backupScheduler: BackupScheduler
    abstract val performBackup: PerformBackup

    interface Dependencies {
        val backupUseCase: BackupUseCase
        val syncEngine: SyncEngine
        val currentUserRepository: CurrentUserRepository
        val workManagerScheduler: WorkManagerScheduler
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerWorkSchedulerComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<WorkSchedulerComponent> {
        fun dependencies(dependencies: Dependencies): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @WorkSchedulerScope
        fun backupScheduler(
            workManagerScheduler: WorkManagerScheduler,
        ): BackupScheduler = DefaultBackupScheduler(workManagerScheduler)

        @Provides
        @WorkSchedulerScope
        fun performBackup(
            backupUseCase: BackupUseCase,
            syncEngine: SyncEngine,
            currentUserRepository: CurrentUserRepository,
        ): PerformBackup = PerformBackup(
            backupUseCase = backupUseCase,
            syncEngine = syncEngine,
            currentUserRepository = currentUserRepository,
        )
    }
}
