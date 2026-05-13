package com.hluhovskyi.zero.user

import com.hluhovskyi.zero.transactions.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DefaultNewUserUseCase(
    private val transactionRepository: TransactionRepository,
) : NewUserUseCase {

    override fun isNewUser(): Flow<Boolean> = transactionRepository.query(TransactionRepository.Criteria.HasAny())
        .map { hasAny -> !hasAny }
        .distinctUntilChanged()
}
