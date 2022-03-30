package com.hluhovskyi.zero

import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.Uri
import com.hluhovskyi.zero.common.d
import com.hluhovskyi.zero.resource.ResourceResolver
import com.hluhovskyi.zero.resource.ResourceStatus
import com.hluhovskyi.zero.resource.UriRequest
import com.hluhovskyi.zero.resource.UriResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

private const val TAG = "CsvCategoryRepository"

internal class CsvCategoryRepository(
    private val importUri: Uri,
    private val resourceResolver: ResourceResolver,
    private val idGenerator: IdGenerator,
    logger: Logger
) : CategoryRepository {

    private val logger = logger.withTag(TAG)

    override fun query(criteria: CategoryRepository.Criteria): Flow<List<CategoryRepository.Category>> {
        return resourceResolver.resolve(UriRequest(importUri))
            .filterIsInstance<ResourceStatus.Result<UriResult>>()
            .map { (result) ->
                result.inputStream.bufferedReader()
                    .lineSequence()
                    .drop(1)
                    .mapNotNull { line -> line.split(';')[1] }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .mapNotNull { name ->
                        CategoryRepository.Category(
                            id = idGenerator.invoke(),
                            parentCategoryId = Id.Unknown,
                            name = name,
                            iconId = Id.Unknown,
                            colorId = Id.Unknown
                        )
                    }
                    .toList()
                    .also {
                        logger.d("query, categories=${it.joinToString(separator = "\n")}")
                    }
            }
    }

    override suspend fun insert(category: CategoryRepository.CategoryInsert) = Unit
}