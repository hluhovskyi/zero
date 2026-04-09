package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Amount
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import com.hluhovskyi.zero.resource.UriResult
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate as JavaLocalDate

private const val TAG = "ZenMoneyImportSourceUseCase"

private val DATE_PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

internal class ZenMoneyImportSourceUseCase(
    private val resourceResolver: ResourceResolver,
    private val idGenerator: IdGenerator,
    logger: Logger,
    private val dateParser: (String) -> LocalDateTime = {
        JavaLocalDate.parse(it, DATE_PARSER).atStartOfDay().toKotlinLocalDateTime()
    }
) : ImportSourceUseCase {

    private val logger = logger.withTag(TAG)

    override suspend fun load(request: ImportSourceUseCase.Request): ImportSourceUseCase.Result =
        when (request) {
            is ImportSourceUseCase.Request.FromFile -> loadFromFile(request.uri)
        }

    private suspend fun loadFromFile(uri: Uri): ImportSourceUseCase.Result {
        val resolveResult = resourceResolver.resolve(UriRequest(uri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .firstOrNull()

        if (resolveResult == null) {
            return ImportSourceUseCase.Result.empty()
        }

        val reader = resolveResult.result.inputStream.bufferedReader()
        val rawData = try {
            val header = reader.readLine()
            if (header == null) {
                emptyList()
            } else {
                val indices = header.removePrefix("\uFEFF").parseIndices()
                reader.lineSequence()
                    .mapNotNull { line -> line.parseRawData(indices) }
                    .toList()
            }
        } finally {
            reader.close()
        }

        val nameToAccounts = LinkedHashMap<String, ImportAccount>()
        val nameToCategories = LinkedHashMap<String, ImportCategory>()
        val transactions = ArrayList<ImportTransaction>()

        rawData.forEach { data ->
            val outcomeAccount = data.outcomeAccountName?.takeIf { it.isNotBlank() }
                ?.let { name ->
                    data.outcomeCurrencyShortTitle?.let { currency ->
                        nameToAccounts.getOrPut(name) {
                            ImportAccount(
                                id = idGenerator(),
                                name = name,
                                currencyId = Id.Known(currency),
                            )
                        }
                    }
                }
            val incomeAccount = data.incomeAccountName?.takeIf { it.isNotBlank() }
                ?.let { name ->
                    data.incomeCurrencyShortTitle?.let { currency ->
                        nameToAccounts.getOrPut(name) {
                            ImportAccount(
                                id = idGenerator(),
                                name = name,
                                currencyId = Id.Known(currency)
                            )
                        }
                    }
                }
            if (outcomeAccount == null && incomeAccount == null) {
                logger.d("loadFromFile, outcomeAccount=null and incomeAccount=null, data=$data")
                return@forEach
            }

            val outcomeAmountValue = data.outcome?.toDoubleOrNull()
            val outcomeAmount = outcomeAmountValue?.let { Amount(it) }

            val incomeAmountValue = data.income?.toDoubleOrNull()
            val incomeAmount = incomeAmountValue?.let { Amount(it) }

            if (outcomeAmount == null && incomeAmount == null) {
                logger.d("loadFromFile, outcomeAmount=null and incomeAmount=null, data=$data")
                return@forEach
            }

            val createdDate = data.date?.let(dateParser)
            if (createdDate == null) {
                logger.d("loadFromFile, createdDate=null, data=$data")
                return@forEach
            }

            if (incomeAccount != null && outcomeAccount != null && incomeAccount != outcomeAccount) {
                if (incomeAmount != null && outcomeAmount != null) {
                    transactions += ImportTransaction.Transfer(
                        id = idGenerator(),
                        accountId = outcomeAccount.id,
                        amount = outcomeAmount,
                        currencyId = outcomeAccount.currencyId,
                        targetAccount = incomeAccount.id,
                        targetAmount = incomeAmount,
                        dateTime = createdDate
                    )
                    return@forEach
                } else {
                    logger.d("loadFromFile, outcomeAmount=$outcomeAccount or incomeAmount=$incomeAccount, data=$data")
                }
            }

            val category = data.categoryName?.takeIf { it.isNotBlank() }?.let { name ->
                nameToCategories.getOrPut(name) {
                    ImportCategory(
                        id = idGenerator(),
                        name = name,
                    )
                }
            }
            if (category == null) {
                logger.d("loadFromFile, category=null, data=$data")
                return@forEach
            }

            if (outcomeAccount != null && outcomeAmountValue != null && outcomeAmountValue > 0) {
                transactions += ImportTransaction.Expense(
                    id = idGenerator(),
                    accountId = outcomeAccount.id,
                    amount = outcomeAmount!!,
                    currencyId = outcomeAccount.currencyId,
                    categoryId = category.id,
                    dateTime = createdDate,
                )
                if (incomeAmountValue != null && incomeAmountValue > 0) {
                    logger.d("loadFromFile, expense is created, but income is greater ")
                }
                return@forEach
            }

            if (incomeAccount != null && incomeAmountValue != null && incomeAmountValue > 0) {
                transactions += ImportTransaction.Income(
                    id = idGenerator(),
                    accountId = incomeAccount.id,
                    amount = incomeAmount!!,
                    currencyId = incomeAccount.currencyId,
                    categoryId = category.id,
                    dateTime = createdDate,
                )
                return@forEach
            }
        }

        return ImportSourceUseCase.Result(
            accounts = nameToAccounts.values.sortedBy { it.name },
            categories = nameToCategories.values.sortedBy { it.name },
            transactions = transactions
        )
    }

    private fun String.parseRawData(indices: Indices): RawData {
        val cells = split(';')
        return RawData(
            categoryName = cells.getOrNull(indices.categoryName)?.withoutBrackets(),
            outcomeAccountName = cells.getOrNull(indices.outcomeAccountName)?.withoutBrackets(),
            outcome = cells.getOrNull(indices.outcome)?.withoutBrackets(),
            outcomeCurrencyShortTitle = cells.getOrNull(indices.outcomeCurrencyShortTitle),
            incomeAccountName = cells.getOrNull(indices.incomeAccountName)?.withoutBrackets(),
            income = cells.getOrNull(indices.income)?.withoutBrackets(),
            incomeCurrencyShortTitle = cells.getOrNull(indices.incomeCurrencyShortTitle),
            date = cells.getOrNull(indices.date),
            createdDate = cells.getOrNull(indices.createdDate),
        )
    }

    private data class RawData(
        val categoryName: String?,
        val outcomeAccountName: String?,
        val outcome: String?,
        val outcomeCurrencyShortTitle: String?,
        val incomeAccountName: String?,
        val income: String?,
        val incomeCurrencyShortTitle: String?,
        val date: String?,
        val createdDate: String?,
    )

    private fun String.parseIndices(): Indices {
        val cells = split(';')
        logger.d("parseIndices, cells=${cells.joinToString()}")
        return Indices(
            date = cells.indexOf("date"),
            categoryName = cells.indexOf("categoryName"),
            payee = cells.indexOf("payee"),
            comment = cells.indexOf("comment"),
            outcomeAccountName = cells.indexOf("outcomeAccountName"),
            outcome = cells.indexOf("outcome"),
            outcomeCurrencyShortTitle = cells.indexOf("outcomeCurrencyShortTitle"),
            incomeAccountName = cells.indexOf("incomeAccountName"),
            income = cells.indexOf("income"),
            incomeCurrencyShortTitle = cells.indexOf("incomeCurrencyShortTitle"),
            createdDate = cells.indexOf("createdDate"),
            changedDate = cells.indexOf("changedDate"),
            qrCode = cells.indexOf("qrCode"),
        )
    }

    private data class Indices(
        val date: Int,
        val categoryName: Int,
        val payee: Int,
        val comment: Int,
        val outcomeAccountName: Int,
        val outcome: Int,
        val outcomeCurrencyShortTitle: Int,
        val incomeAccountName: Int,
        val income: Int,
        val incomeCurrencyShortTitle: Int,
        val createdDate: Int,
        val changedDate: Int,
        val qrCode: Int
    )

    private fun String.withoutBrackets(): String {
        if (!startsWith('"')) {
            return this
        }

        if (!endsWith('"')) {
            return this
        }

        return substring(1, length - 1)
    }
}
