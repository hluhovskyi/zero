package com.hluhovskyi.zero.imports

import com.hluhovskyi.zero.common.Uri

interface ImportSourceUseCase {

    suspend fun load(request: Request): Result

    sealed interface Request {
        data class FromFile(val uri: Uri) : Request
    }

    data class Result(
        val accounts: List<ImportAccount> = emptyList(),
        val categories: List<ImportCategory> = emptyList(),
        val transactions: List<ImportTransaction> = emptyList(),
    ) {

        companion object {

            private val EMPTY = Result()

            fun empty(): Result = EMPTY
        }
    }

    object Noop : ImportSourceUseCase {
        override suspend fun load(request: Request): Result = Result.empty()
    }
}

fun (() -> ImportSourceUseCase).lazy(): ImportSourceUseCase {
    return lazy(this).let {
        object : ImportSourceUseCase {
            override suspend fun load(request: ImportSourceUseCase.Request): ImportSourceUseCase.Result =
                it.value.load(request)
        }
    }
}