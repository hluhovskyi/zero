package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.accountsreview.AccountsReviewComponent
import com.hluhovskyi.zero.imports.categoriesreview.CategoriesReviewComponent
import com.hluhovskyi.zero.imports.sourceselection.SourceSelectionComponent
import com.hluhovskyi.zero.imports.transactionspreview.TransactionsPreviewComponent
import com.hluhovskyi.zero.sync.SyncEngine
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
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter
    }

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerImportComponent.builder()
            .dependencies(dependencies)
            .parsers(emptyList())
            .onImportFinishedHandler(OnImportFinishedHandler.Noop)

        fun builder(parsers: List<@JvmSuppressWildcards SnapshotParser>): Builder = DaggerImportComponent.builder()
            .parsers(parsers)
            .onImportFinishedHandler(OnImportFinishedHandler.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ImportComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun parsers(parsers: List<@JvmSuppressWildcards SnapshotParser>): Builder

        @BindsInstance
        fun onImportFinishedHandler(handler: OnImportFinishedHandler): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @ImportScope
        fun useCase(
            parsers: List<@JvmSuppressWildcards SnapshotParser>,
            syncEngine: SyncEngine,
            currentUserRepository: CurrentUserRepository,
            onImportFinishedHandler: OnImportFinishedHandler,
        ): ImportUseCase = DefaultImportUseCase(
            parsers = parsers,
            syncEngine = syncEngine,
            currentUserRepository = currentUserRepository,
            onImportFinishedHandler = onImportFinishedHandler,
        )

        @Provides
        @ImportScope
        internal fun sourceSelectionComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
            onImportFinishedHandler: OnImportFinishedHandler,
        ): SourceSelectionComponent.Builder = SourceSelectionComponent.builder(component)
            .importUseCase(importUseCase)
            .onImportFinishedHandler(onImportFinishedHandler)

        @Provides
        @ImportScope
        internal fun categoriesReviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
        ): CategoriesReviewComponent.Builder = CategoriesReviewComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun accountsReviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
        ): AccountsReviewComponent.Builder = AccountsReviewComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun transactionsPreviewComponentBuilder(
            component: ImportComponent,
            importUseCase: ImportUseCase,
        ): TransactionsPreviewComponent.Builder = TransactionsPreviewComponent.builder(component)
            .importUseCase(importUseCase)

        @Provides
        @ImportScope
        internal fun viewProvider(
            importUseCase: ImportUseCase,
            sourceSelectionBuilder: SourceSelectionComponent.Builder,
            categoriesReviewBuilder: CategoriesReviewComponent.Builder,
            accountsReviewBuilder: AccountsReviewComponent.Builder,
            transactionsPreviewBuilder: TransactionsPreviewComponent.Builder,
        ): ViewProvider = ImportViewProvider(
            useCase = importUseCase,
            sourceSelection = sourceSelectionBuilder,
            categoriesReview = categoriesReviewBuilder,
            accountsReview = accountsReviewBuilder,
            transactionsPreview = transactionsPreviewBuilder,
        )
    }
}
