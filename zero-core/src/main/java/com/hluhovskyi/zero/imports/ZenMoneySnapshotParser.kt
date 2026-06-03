package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import com.hluhovskyi.zero.resource.UriResult
import com.hluhovskyi.zero.sync.SyncAccount
import com.hluhovskyi.zero.sync.SyncCategory
import com.hluhovskyi.zero.sync.SyncSnapshot
import com.hluhovskyi.zero.sync.SyncTransaction
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.LocalDate as JavaLocalDate

private const val TAG = "ZenMoneySnapshotParser"
private val DATE_PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val PLACEHOLDER_USER_ID = Id.Known("zenmoney-import")

class ZenMoneySnapshotParser(
    private val resourceResolver: ResourceResolver,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    logger: Logger,
) : SnapshotProvider.File {

    private val logger = logger.withTag(TAG)
    override val source: Source = KnownSource.ZenMoney

    override suspend fun parse(uri: Uri.NonEmpty): SyncSnapshot {
        val resolveResult = resourceResolver.resolve(UriRequest(uri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .firstOrNull() ?: return emptySnapshot()

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

        val now = clock.now().toLocalDateTime(TimeZone.UTC)
        val idToAccount = LinkedHashMap<String, SyncAccount>()
        val idToCategory = LinkedHashMap<String, SyncCategory>()
        val transactions = ArrayList<SyncTransaction>()

        fun getOrCreateAccount(name: String, currencyCode: String): SyncAccount = idToAccount.getOrPut(name) {
            SyncAccount(
                id = idGenerator(),
                currencyId = Id.Known(currencyCode),
                name = name,
                iconId = Id.Known("account-default"),
                initialBalance = "0",
                category = "OTHER",
                details = null,
                creationDateTime = now,
                updatedDateTime = now,
                deletedAt = null,
            )
        }

        fun getOrCreateCategory(name: String): SyncCategory = idToCategory.getOrPut(name) {
            SyncCategory(
                id = idGenerator(),
                name = name,
                iconId = null,
                colorId = null,
                parentCategoryId = null,
                creationDateTime = now,
                updatedDateTime = now,
                deletedAt = null,
            )
        }

        rawData.forEach { data ->
            val outcomeAccount = data.outcomeAccountName?.takeIf { it.isNotBlank() }
                ?.let { name -> data.outcomeCurrencyShortTitle?.let { getOrCreateAccount(name, it) } }
            val incomeAccount = data.incomeAccountName?.takeIf { it.isNotBlank() }
                ?.let { name -> data.incomeCurrencyShortTitle?.let { getOrCreateAccount(name, it) } }

            if (outcomeAccount == null && incomeAccount == null) {
                logger.d("parse: both accounts null, data=$data")
                return@forEach
            }

            val outcomeAmountValue = data.outcome?.toDoubleOrNull()
            val incomeAmountValue = data.income?.toDoubleOrNull()

            if (outcomeAmountValue == null && incomeAmountValue == null) {
                logger.d("parse: both amounts null, data=$data")
                return@forEach
            }

            val txDate: LocalDateTime = data.date?.let {
                runCatching {
                    JavaLocalDate.parse(it, DATE_PARSER).atStartOfDay().toKotlinLocalDateTime()
                }.getOrNull()
            } ?: run {
                logger.d("parse: invalid date, data=$data")
                return@forEach
            }

            val txId = idGenerator()

            if (incomeAccount != null &&
                outcomeAccount != null &&
                incomeAccount.id != outcomeAccount.id &&
                incomeAmountValue != null &&
                outcomeAmountValue != null
            ) {
                transactions += SyncTransaction(
                    id = txId,
                    type = SyncTransaction.Type.TRANSFER,
                    accountId = outcomeAccount.id,
                    currencyId = outcomeAccount.currencyId,
                    categoryId = null,
                    amount = outcomeAmountValue.toString(),
                    rate = "1.0",
                    targetAccountId = incomeAccount.id.value,
                    targetAmount = incomeAmountValue.toString(),
                    enteredDateTime = txDate,
                    creationDateTime = txDate,
                    updatedDateTime = txDate,
                    deletedAt = null,
                )
                return@forEach
            }

            val category = data.categoryName?.takeIf { it.isNotBlank() }
                ?.let { getOrCreateCategory(it) }
            if (category == null) {
                logger.d("parse: no category, data=$data")
                return@forEach
            }

            if (outcomeAccount != null && outcomeAmountValue != null && outcomeAmountValue > 0) {
                transactions += SyncTransaction(
                    id = txId,
                    type = SyncTransaction.Type.EXPENSE,
                    accountId = outcomeAccount.id,
                    currencyId = outcomeAccount.currencyId,
                    categoryId = category.id.value,
                    amount = outcomeAmountValue.toString(),
                    rate = "1.0",
                    targetAccountId = null,
                    targetAmount = null,
                    enteredDateTime = txDate,
                    creationDateTime = txDate,
                    updatedDateTime = txDate,
                    deletedAt = null,
                )
                return@forEach
            }

            if (incomeAccount != null && incomeAmountValue != null && incomeAmountValue > 0) {
                transactions += SyncTransaction(
                    id = txId,
                    type = SyncTransaction.Type.INCOME,
                    accountId = incomeAccount.id,
                    currencyId = incomeAccount.currencyId,
                    categoryId = category.id.value,
                    amount = incomeAmountValue.toString(),
                    rate = "1.0",
                    targetAccountId = null,
                    targetAmount = null,
                    enteredDateTime = txDate,
                    creationDateTime = txDate,
                    updatedDateTime = txDate,
                    deletedAt = null,
                )
            }
        }

        return SyncSnapshot(
            version = 1,
            userId = PLACEHOLDER_USER_ID,
            exportedAt = now,
            categories = idToCategory.values.sortedBy { it.name },
            accounts = idToAccount.values.sortedBy { it.name },
            transactions = transactions,
        )
    }

    private fun emptySnapshot(): SyncSnapshot {
        val now = clock.now().toLocalDateTime(TimeZone.UTC)
        return SyncSnapshot(
            version = 1,
            userId = PLACEHOLDER_USER_ID,
            exportedAt = now,
            categories = emptyList(),
            accounts = emptyList(),
            transactions = emptyList(),
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
    )

    private fun String.parseIndices(): Indices {
        val cells = split(';')
        return Indices(
            date = cells.indexOf("date"),
            categoryName = cells.indexOf("categoryName"),
            outcomeAccountName = cells.indexOf("outcomeAccountName"),
            outcome = cells.indexOf("outcome"),
            outcomeCurrencyShortTitle = cells.indexOf("outcomeCurrencyShortTitle"),
            incomeAccountName = cells.indexOf("incomeAccountName"),
            income = cells.indexOf("income"),
            incomeCurrencyShortTitle = cells.indexOf("incomeCurrencyShortTitle"),
        )
    }

    private data class Indices(
        val date: Int,
        val categoryName: Int,
        val outcomeAccountName: Int,
        val outcome: Int,
        val outcomeCurrencyShortTitle: Int,
        val incomeAccountName: Int,
        val income: Int,
        val incomeCurrencyShortTitle: Int,
    )

    private fun String.withoutBrackets(): String {
        if (!startsWith('"') || !endsWith('"')) return this
        return substring(1, length - 1)
    }
}
