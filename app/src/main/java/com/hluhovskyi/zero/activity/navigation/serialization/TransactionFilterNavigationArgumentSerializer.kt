package com.hluhovskyi.zero.activity.navigation.serialization

import com.hluhovskyi.zero.activity.navigation.Argument
import com.hluhovskyi.zero.activity.navigation.ArgumentValue
import com.hluhovskyi.zero.activity.navigation.withValue
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.transactions.TransactionFilter
import kotlin.reflect.KClass

/**
 * Serializes a [TransactionFilter] as one route-safe nav argument: the four fields are joined and
 * hex-encoded (pure-JVM, so it unit-tests without `android.util.Base64`). A null id-set encodes as an
 * empty field and decodes back to null.
 */
internal object TransactionFilterNavigationArgumentSerializer : TypedNavigationArgumentSerializer<TransactionFilter>() {

    override val actualClass: KClass<TransactionFilter> = TransactionFilter::class

    override fun performSerialization(argumentValue: ArgumentValue<TransactionFilter>): String {
        val filter = argumentValue.value
        val payload = listOf(
            filter.period?.name.orEmpty(),
            filter.type.name,
            filter.categoryIds?.joinToString(ID_SEPARATOR) { it.value }.orEmpty(),
            filter.accountIds?.joinToString(ID_SEPARATOR) { it.value }.orEmpty(),
        ).joinToString(FIELD_SEPARATOR)
        return payload.encodeHex()
    }

    override fun performDeserialization(argument: Argument<TransactionFilter>, rawValue: String): ArgumentValue<TransactionFilter> {
        val fields = rawValue.decodeHex().split(FIELD_SEPARATOR)
        return argument.withValue(
            TransactionFilter(
                period = fields.getOrNull(0).nullIfEmpty()?.let { TransactionFilter.DatePeriod.valueOf(it) },
                type = fields.getOrNull(1).nullIfEmpty()?.let { TransactionFilter.TransactionType.valueOf(it) }
                    ?: TransactionFilter.TransactionType.All,
                categoryIds = fields.getOrNull(2).toIdSet(),
                accountIds = fields.getOrNull(3).toIdSet(),
            ),
        )
    }

    private fun String?.nullIfEmpty(): String? = this?.takeIf { it.isNotEmpty() }

    private fun String?.toIdSet(): Set<Id.Known>? = nullIfEmpty()?.split(ID_SEPARATOR)?.map { Id.Known(it) }?.toSet()

    private fun String.encodeHex(): String = encodeToByteArray().joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun String.decodeHex(): String = ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }.decodeToString()

    private const val FIELD_SEPARATOR = "|"
    private const val ID_SEPARATOR = ","
}
