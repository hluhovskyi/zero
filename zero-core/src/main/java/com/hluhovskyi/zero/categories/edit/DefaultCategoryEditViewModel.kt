package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.common.Closeables
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.Closeable

internal class DefaultCategoryEditViewModel : CategoryEditViewModel {

    override val state: Flow<CategoryEditViewModel.State> = emptyFlow()

    override fun perform(action: CategoryEditViewModel.Action) {
    }

    override fun attach(): Closeable = Closeables.empty()
}