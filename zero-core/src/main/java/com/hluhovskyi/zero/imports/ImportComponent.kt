package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.imports.accountsreview.AccountsReviewComponent
import com.hluhovskyi.zero.imports.categoriesreview.CategoriesReviewComponent
import com.hluhovskyi.zero.imports.sourceselection.SourceSelectionComponent
import com.hluhovskyi.zero.imports.transactionspreview.TransactionsPreviewComponent
import com.hluhovskyi.zero.sync.SyncEngine
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
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
abstract class ImportComponent :
    AttachableViewComponent,
    SourceSelectionComponent.Dependencies,
    CategoriesReviewComponent.Dependencies,
    AccountsReviewComponent.Dependencies,
    TransactionsPreviewComponent.Dependencies {

    override val tag: String = TAG

    internal abstract val useCase: ImportUseCase
    override fun attach(): Closeable = useCase.attach()

    interface Dependencies {
        val syncEngine: SyncEngine
        val currentUserRepository: CurrentUserRepository
        val iconRepository: IconRepository
        val colorRepository: ColorRepository
        val categoryRepository: CategoryRepository
        val accountRepository: AccountRepository
        val transactionRepository: TransactionRepository
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerImportComponent.builder()
            .dependencies(dependencies)
            .parsers(emptyList())
            .initialSource(InitialSource(null))
            .onImportFinishedHandler(OnImportFinishedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun parsers(parsers: List<SnapshotParser>): Builder

        @BindsInstance
        fun initialSource(initialSource: InitialSource): Builder

        @BindsInstance
        fun onImportFinishedHandler(handler: OnImportFinishedHandler): Builder
    }

    /** Optional source key to auto-select on open, skipping the picker (e.g. "drive"). */
    class InitialSource(val key: String?)

    @dagger.Module
    object Module {

        @Provides
        @ImportScope
        fun useCase(
            parsers: List<SnapshotParser>,
            syncEngine: SyncEngine,
            currentUserRepository: CurrentUserRepository,
            iconRepository: IconRepository,
            colorRepository: ColorRepository,
            categoryRepository: CategoryRepository,
            accountRepository: AccountRepository,
            transactionRepository: TransactionRepository,
            onImportFinishedHandler: OnImportFinishedHandler,
            initialSource: InitialSource,
        ): ImportUseCase = DefaultImportUseCase(
            parsers = parsers,
            syncEngine = syncEngine,
            currentUserRepository = currentUserRepository,
            iconRepository = iconRepository,
            colorRepository = colorRepository,
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            onImportFinishedHandler = onImportFinishedHandler,
            initialSourceKey = initialSource.key,
        )

        @Provides
        @ImportScope
        fun viewModel(useCase: ImportUseCase): ImportViewModel = DefaultImportViewModel(useCase)

        @Provides
        internal fun sourceSelectionComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
            onImportFinishedHandler: OnImportFinishedHandler,
        ): SourceSelectionComponent.Builder = SourceSelectionComponent.builder(component)
            .importUseCase(importUseCase)
            .onImportFinishedHandler(onImportFinishedHandler)

        @Provides
        internal fun categoriesReviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
            imageLoader: ImageLoader,
        ): CategoriesReviewComponent.Builder = CategoriesReviewComponent.builder(component)
            .importUseCase(importUseCase)
            .imageLoader(imageLoader)

        @Provides
        internal fun accountsReviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
            imageLoader: ImageLoader,
        ): AccountsReviewComponent.Builder = AccountsReviewComponent.builder(component)
            .importUseCase(importUseCase)
            .imageLoader(imageLoader)

        @Provides
        internal fun transactionsPreviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
            imageLoader: ImageLoader,
        ): TransactionsPreviewComponent.Builder = TransactionsPreviewComponent.builder(component)
            .importUseCase(importUseCase)
            .imageLoader(imageLoader)

        @Provides
        @ImportScope
        internal fun viewProvider(
            viewModel: ImportViewModel,
            sourceSelectionBuilder: SourceSelectionComponent.Builder,
            categoriesReviewBuilder: CategoriesReviewComponent.Builder,
            accountsReviewBuilder: AccountsReviewComponent.Builder,
            transactionsPreviewBuilder: TransactionsPreviewComponent.Builder,
        ): ViewProvider = ImportViewProvider(
            viewModel = viewModel,
            sourceSelection = sourceSelectionBuilder,
            categoriesReview = categoriesReviewBuilder,
            accountsReview = accountsReviewBuilder,
            transactionsPreview = transactionsPreviewBuilder,
        )
    }
}
