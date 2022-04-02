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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "ZenMoneyImportSourceUseCase"

private val DATE_PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

internal class ZenMoneyImportSourceUseCase(
    private val resourceResolver: ResourceResolver,
    private val idGenerator: IdGenerator,
    logger: Logger,
    private val dateParser: (String) -> LocalDateTime = {
        LocalDate.parse(it, DATE_PARSER).atStartOfDay()
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

        val rawData = resolveResult.result.inputStream.bufferedReader().use { reader ->
            val indices = reader.readLine().removePrefix("\uFEFF").parseIndices()

            reader.lineSequence()
                .mapNotNull { line -> line.parseRawData(indices) }
                .toList()
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
                                currencyId = Id(currency),
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
                                currencyId = Id(currency)
                            )
                        }
                    }
                }
            if (outcomeAccount == null && incomeAccount == null) {
                logger.d("loadFromFile, outcomeAccount=null and incomeAccount=null, data=$data")
                return@forEach
            }

            val outcomeAmount = data.outcome?.toDoubleOrNull()?.let {
                Amount(it)
            }
            val incomeAmount = data.income?.toDoubleOrNull()?.let {
                Amount(it)
            }
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

            if (outcomeAccount != null && outcomeAmount?.let { it > 0 } == true) {
                transactions += ImportTransaction.Expense(
                    id = idGenerator(),
                    accountId = outcomeAccount.id,
                    amount = outcomeAmount,
                    currencyId = outcomeAccount.currencyId,
                    categoryId = category.id,
                    dateTime = createdDate,
                )
                if (incomeAmount?.let { it > 0 } == true) {
                    logger.d("loadFromFile, expense is created, but income is greater ")
                }
                return@forEach
            }

            if (incomeAccount != null && incomeAmount?.let { it > 0 } == true) {
                transactions += ImportTransaction.Income(
                    id = idGenerator(),
                    accountId = incomeAccount.id,
                    amount = incomeAmount,
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
            categoryName = cells[indices.categoryName].withoutBrackets(),
            outcomeAccountName = cells[indices.outcomeAccountName].withoutBrackets(),
            outcome = cells[indices.outcome].withoutBrackets(),
            outcomeCurrencyShortTitle = cells[indices.outcomeCurrencyShortTitle],
            incomeAccountName = cells[indices.incomeAccountName].withoutBrackets(),
            income = cells[indices.income].withoutBrackets(),
            incomeCurrencyShortTitle = cells[indices.incomeCurrencyShortTitle],
            date = cells[indices.date],
            createdDate = cells[indices.createdDate],
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