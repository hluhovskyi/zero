package com.hluhovskyi.zero.categories.edit

import com.hluhovskyi.zero.common.AttachableStateViewModel

interface CategoryEditViewModel
    : AttachableStateViewModel<CategoryEditViewModel.Action, CategoryEditViewModel.State> {

    sealed class Action {

    }

    sealed class State {

    }
}