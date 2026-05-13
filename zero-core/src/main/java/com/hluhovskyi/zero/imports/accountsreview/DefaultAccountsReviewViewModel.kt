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
        .map { AccountsReviewViewModel.State(it.accounts, it.strategies) }

    override fun perform(action: AccountsReviewViewModel.Action) {
        when (action) {
            is AccountsReviewViewModel.Action.Next ->
                importUseCase.perform(ImportUseCase.Action.ConfirmAccounts)
            is AccountsReviewViewModel.Action.Back ->
                importUseCase.perform(ImportUseCase.Action.Back)
            is AccountsReviewViewModel.Action.SetStrategy ->
                importUseCase.perform(ImportUseCase.Action.SetAccountStrategy(action.id, action.strategy))
        }
    }
}
