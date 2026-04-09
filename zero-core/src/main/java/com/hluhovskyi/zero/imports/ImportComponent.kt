package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.accounts.ImportAccountPickerComponent
import com.hluhovskyi.zero.imports.categories.ImportCategoriesPickerComponent
import com.hluhovskyi.zero.imports.filepicker.ImportFilePickerComponent
import com.hluhovskyi.zero.imports.transactions.ImportTransactionPreviewComponent
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ImportScope

private const val TAG = "ImportComponent"

@ImportScope
@dagger.Component(
    modules = [ImportComponent.Module::class],
    dependencies = [ImportComponent.Dependencies::class],
)
abstract class ImportComponent : AttachableViewComponent,
    ImportFilePickerComponent.Dependencies,
    ImportCategoriesPickerComponent.Dependencies,
    ImportAccountPickerComponent.Dependencies,
    ImportTransactionPreviewComponent.Dependencies {

    override val tag: String = TAG

    internal abstract val useCase: ImportUseCase
    override fun attach(): Closeable = useCase.attach()

    interface Dependencies {
        val accountRepository: AccountRepository
        val categoryRepository: CategoryRepository
        val transactionRepository: TransactionRepository
        val clock: Clock
        val zoneProvider: ZoneProvider
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerImportComponent.builder()
            .dependencies(dependencies)
            .importSourceUseCase(ImportSourceUseCase.Noop)
            .onImportFinishedHandler(OnImportFinishedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importSourceUseCase(importSourceUseCase: ImportSourceUseCase): Builder

        @BindsInstance
        fun onImportFinishedHandler(handler: OnImportFinishedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ImportScope
        fun useCase(
            importSourceUseCase: ImportSourceUseCase,
            accountRepository: AccountRepository,
            categoryRepository: CategoryRepository,
            transactionRepository: TransactionRepository,
            onImportFinishedHandler: OnImportFinishedHandler,
            clock: Clock,
            zoneProvider: ZoneProvider,
        ): ImportUseCase = DefaultImportUseCase(
            importSourceUseCase = importSourceUseCase,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            transactionRepository = transactionRepository,
            onImportFinishedHandler = onImportFinishedHandler,
            clock = clock,
            zoneProvider = zoneProvider,
        )

        @Provides
        @ImportScope
        fun viewModel(
            importUseCase: ImportUseCase
        ): ImportViewModel = DefaultImportViewModel(
            importUseCase = importUseCase
        )

        @Provides
        @ImportScope
        internal fun filePickerComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase
        ): ImportFilePickerComponent.Builder = ImportFilePickerComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun accountPickerComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase
        ): ImportAccountPickerComponent.Builder = ImportAccountPickerComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun categoryPickerComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
        ): ImportCategoriesPickerComponent.Builder = ImportCategoriesPickerComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun transactionsPreviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
        ): ImportTransactionPreviewComponent.Builder = ImportTransactionPreviewComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun viewProvider(
            viewModel: ImportViewModel,
            filePickerComponentBuilder: ImportFilePickerComponent.Builder,
            accountPickerComponentBuilder: ImportAccountPickerComponent.Builder,
            categoriesPickerComponentBuilder: ImportCategoriesPickerComponent.Builder,
            transactionsPreviewComponentBuilder: ImportTransactionPreviewComponent.Builder,
        ): ViewProvider = ImportViewProvider(
            viewModel = viewModel,
            filePicker = filePickerComponentBuilder,
            accountPicker = accountPickerComponentBuilder,
            categoriesPicker = categoriesPickerComponentBuilder,
            transactionsPreview = transactionsPreviewComponentBuilder,
        )
    }
}