package com.hluhovskyi.zero.settings

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.export.DefaultExportUseCase
import com.hluhovskyi.zero.export.ExportUseCase
import com.hluhovskyi.zero.export.ExportWriter
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.sync.SyncSerializer
import com.hluhovskyi.zero.users.CurrentUserRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class SettingsScope

private const val TAG = "SettingsComponent"

@SettingsScope
@dagger.Component(
    modules = [SettingsComponent.Module::class],
    dependencies = [SettingsComponent.Dependencies::class],
)
abstract class SettingsComponent : AttachableViewComponent {

    override val tag: String = TAG

    internal abstract val viewModel: SettingsViewModel
    override fun attach(): Closeable = viewModel.attach()

    interface Dependencies {
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val syncEngine: SyncEngine
        val currentUserRepository: CurrentUserRepository
        val serializer: SyncSerializer
        val exportWriter: ExportWriter
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerSettingsComponent.builder()
            .dependencies(dependencies)
            .onImportSelectedHandler(OnImportSelectedHandler.Noop)
            .settingsCurrencyUseCase(SettingsCurrencyUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<SettingsComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun onImportSelectedHandler(handler: OnImportSelectedHandler): Builder

        @BindsInstance
        fun settingsCurrencyUseCase(useCase: SettingsCurrencyUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @SettingsScope
        fun exportUseCase(
            currentUserRepository: CurrentUserRepository,
            syncEngine: SyncEngine,
            serializer: SyncSerializer,
            exportWriter: ExportWriter,
        ): ExportUseCase = DefaultExportUseCase(
            currentUserRepository = currentUserRepository,
            syncEngine = syncEngine,
            serializer = serializer,
            exportWriter = exportWriter,
        )

        @Provides
        @SettingsScope
        fun viewModel(
            onImportSelected: OnImportSelectedHandler,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            settingsCurrencyUseCase: SettingsCurrencyUseCase,
            exportUseCase: ExportUseCase,
        ): SettingsViewModel = DefaultSettingsViewModel(
            onImportSelected = onImportSelected,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            settingsCurrencyUseCase = settingsCurrencyUseCase,
            exportUseCase = exportUseCase,
        )

        @Provides
        @SettingsScope
        fun viewProvider(viewModel: SettingsViewModel): ViewProvider = SettingsViewProvider(viewModel = viewModel)
    }
}
