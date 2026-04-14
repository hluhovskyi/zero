// zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/AccountsReviewComponent.kt
package com.hluhovskyi.zero.imports.accountsreview

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.imports.ImportUseCase
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class AccountsReviewScope

private const val TAG = "AccountsReviewComponent"

@AccountsReviewScope
@dagger.Component(
    modules = [AccountsReviewComponent.Module::class],
    dependencies = [AccountsReviewComponent.Dependencies::class],
)
internal abstract class AccountsReviewComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies

    companion object {
        fun builder(dependencies: Dependencies): Builder = DaggerAccountsReviewComponent.builder()
            .dependencies(dependencies)
            .importUseCase(ImportUseCase.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<AccountsReviewComponent> {
        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun importUseCase(useCase: ImportUseCase): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @AccountsReviewScope
        fun viewModel(importUseCase: ImportUseCase): AccountsReviewViewModel = DefaultAccountsReviewViewModel(importUseCase = importUseCase)

        @Provides
        @AccountsReviewScope
        fun viewProvider(viewModel: AccountsReviewViewModel): ViewProvider = AccountsReviewViewProvider(viewModel = viewModel)
    }
}
