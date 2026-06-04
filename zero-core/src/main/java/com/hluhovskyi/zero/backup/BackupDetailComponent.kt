package com.hluhovskyi.zero.backup

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.OnBackHandler
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.config.ConfigurationRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class BackupDetailScope

private const val TAG = "BackupDetailComponent"

@BackupDetailScope
@dagger.Component(
    modules = [BackupDetailComponent.Module::class],
    dependencies = [BackupDetailComponent.Dependencies::class],
)
abstract class BackupDetailComponent : AttachableViewComponent {

    internal abstract val viewModel: BackupDetailViewModel

    override val tag: String = TAG
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val backupUseCase: BackupUseCase
        val backupScheduler: BackupScheduler
        val configurationRepository: ConfigurationRepository
        val dispatchers: DispatcherProvider
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerBackupDetailComponent.builder()
            .dependencies(dependencies)
            .onBackHandler(OnBackHandler.Noop)
            .onRestoreSelectedHandler(OnRestoreSelectedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<BackupDetailComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onBackHandler(handler: OnBackHandler): Builder

        @BindsInstance
        fun onRestoreSelectedHandler(handler: OnRestoreSelectedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @BackupDetailScope
        fun viewModel(
            backupUseCase: BackupUseCase,
            backupScheduler: BackupScheduler,
            configurationRepository: ConfigurationRepository,
            onBackHandler: OnBackHandler,
            onRestoreSelectedHandler: OnRestoreSelectedHandler,
            dispatchers: DispatcherProvider,
        ): BackupDetailViewModel = DefaultBackupDetailViewModel(
            backupUseCase = backupUseCase,
            backupScheduler = backupScheduler,
            configurationRepository = configurationRepository,
            onBackHandler = onBackHandler,
            onRestoreSelectedHandler = onRestoreSelectedHandler,
            dispatchers = dispatchers,
        )

        @Provides
        @BackupDetailScope
        fun viewProvider(viewModel: BackupDetailViewModel): ViewProvider = BackupDetailViewProvider(viewModel = viewModel)
    }
}
