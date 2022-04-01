package com.hluhovskyi.zero.imports

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

private const val TAG = "ZenMoneyImportSourceUseCase"

internal class ZenMoneyImportSourceUseCase(
    private val resourceResolver: ResourceResolver,
    private val idGenerator: IdGenerator,
    logger: Logger
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
            val indices = reader.readLine().parseIndices()

            reader.lineSequence()
                .mapNotNull { line -> line.parseRawData(indices) }
                .toList()
        }

        logger.d("rawData, ${rawData.joinToString(separator = "\n")}")

        val accounts = rawData
            .flatMap {
                listOfNotNull(
                    it.incomeAccountName,
                    it.outcomeAccountName
                )
            }
            .distinct()
            .sorted()
            .map { name ->
                ImportAccount(
                    id = idGenerator(),
                    name = name,
                )
            }

        val categories = rawData
            .mapNotNull { it.categoryName }
            .distinct()
            .sorted()
            .map { name ->
                ImportCategory(
                    id = idGenerator(),
                    name = name,
                )
            }

        return ImportSourceUseCase.Result(
            accounts = accounts,
            categories = categories
        )
    }

    private fun String.parseRawData(indices: Indices): RawData {
        val cells = split(';')
        return RawData(
            categoryName = cells[indices.categoryName].withoutBrackets(),
            outcomeAccountName = cells[indices.outcomeAccountName].withoutBrackets(),
            outcome = cells[indices.outcome],
            incomeAccountName = cells[indices.incomeAccountName].withoutBrackets(),
            income = cells[indices.income],
        )
    }

    private data class RawData(
        val categoryName: String?,
        val outcomeAccountName: String?,
        val outcome: String?,
        val incomeAccountName: String?,
        val income: String?,
    )

    private fun String.parseIndices(): Indices {
        val cells = split(';')
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