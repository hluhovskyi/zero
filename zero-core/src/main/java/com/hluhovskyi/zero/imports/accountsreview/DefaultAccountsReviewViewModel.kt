// zero-core/src/main/java/com/hluhovskyi/zero/imports/accountsreview/DefaultAccountsReviewViewModel.kt
package com.hluhovskyi.zero.imports.accountsreview

import com.hluhovskyi.zero.imports.ImportUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

internal class DefaultAccountsReviewViewModel(
    private val importUseCase: ImportUseCase,
) : AccountsReviewViewModel {

    override val state: Flow<AccountsReviewViewModel.State> = importUseCase.state
        .filterIsInstance<ImportUseCase.State.AccountsReview>()
        .map { AccountsReviewViewModel.State(accounts = it.accounts) }

    override fun perform(action: AccountsReviewViewModel.Action) {
        when (action) {
            is AccountsReviewViewModel.Action.Next ->
                importUseCase.perform(ImportUseCase.Action.ConfirmAccounts)
            is AccountsReviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
        }
    }
}
