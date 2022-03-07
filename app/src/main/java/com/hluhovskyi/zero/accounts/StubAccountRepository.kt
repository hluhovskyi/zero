package com.hluhovskyi.zero.accounts

import com.hluhovskyi.zero.common.Account
import com.hluhovskyi.zero.common.Currency
import com.hluhovskyi.zero.common.Id
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class StubAccountRepository : AccountRepository {
    override fun query(criteria: AccountRepository.Criteria): Flow<List<Account>> =
        flowOf(
            listOf(
                Account(
                    id = Id("uah"),
                    name = "UAH Cash",
                    currencyId = Id("UAH")
                ),
                Account(
                    id = Id("usd"),
                    name = "USD Cash",
                    currencyId = Id("USD")
                )
            )
        )
}