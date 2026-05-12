package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.colors.ColorScheme
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.coroutines.associateById
import com.hluhovskyi.zero.common.coroutines.onEmptyReturnEmptyList
import com.hluhovskyi.zero.icons.IconRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class DefaultAccountsQueryUseCase(
    private val accountRepository: AccountRepository,
    private val iconRepository: IconRepository,
    private val colorRepository: ColorRepository,
) : AccountsQueryUseCase {

    override fun queryAll(): Flow<List<AccountsQueryUseCase.Account>> = combine(
        accountRepository.query(AccountRepository.Criteria.All()).onEmptyReturnEmptyList(),
        iconRepository.query(IconRepository.Criteria.All()).onEmptyReturnEmptyList().associateById(),
    ) { accounts, idToIcon ->
        accounts.map { a ->
            AccountsQueryUseCase.Account(
                id = a.id,
                name = a.name,
                colorScheme = (a.colorId as? Id.Known)?.let { colorRepository.schemeFor(it) } ?: ColorScheme.Grey,
                icon = (idToIcon[a.iconId] ?: iconRepository.iconFor(a.category)).image,
            )
        }
    }
}
