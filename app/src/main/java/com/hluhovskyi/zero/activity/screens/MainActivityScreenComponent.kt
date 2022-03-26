package com.hluhovskyi.zero.activity.screens

import androidx.navigation.NavHostController
import com.hluhovskyi.zero.accounts.AccountComponent
import com.hluhovskyi.zero.accounts.edit.AccountEditComponent
import com.hluhovskyi.zero.categories.CategoryComponent
import com.hluhovskyi.zero.categories.edit.CategoryEditComponent
import com.hluhovskyi.zero.categories.edit.CategoryEditIconUseCase
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.logging
import com.hluhovskyi.zero.icons.IconPickerComponent
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.edit.TransactionEditComponent
import dagger.BindsInstance
import dagger.Provides
import dagger.multibindings.IntoSet
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class MainActivityScreenScope

private const val TAG = "MainActivityScreenComponent"

/**
 * Component which is responsible for screens rendering, navigation and communication between ones.
 * All business-logic dependencies of screens should be satisfied before providing to this component.
 */
@MainActivityScreenScope
@dagger.Component(
    dependencies = [MainActivityScreenComponent.Dependencies::class],
    modules = [MainActivityScreenComponent.Module::class]
)
internal abstract class MainActivityScreenComponent : AttachableViewComponent {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {
        val logger: Logger

        val transactionComponentBuilder: TransactionComponent.Builder
        val transactionEditComponentBuilder: TransactionEditComponent.Builder

        val categoryComponentBuilder: CategoryComponent.Builder
        val categoryEditComponentBuilder: CategoryEditComponent.Builder

        val accountComponentBuilder: AccountComponent.Builder
        val accountEditComponentBuilder: AccountEditComponent.Builder

        val iconPickerComponentBuilder: IconPickerComponent.Builder
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerMainActivityScreenComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<MainActivityScreenComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun navHostController(navHostController: NavHostController): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @MainActivityScreenScope
        fun navigator(
            navHostController: NavHostController
        ): Navigator = NavControllerNavigator(
            navController = navHostController
        )

        @Provides
        @MainActivityScreenScope
        fun viewProvider(
            navHostController: NavHostController,
            navigationEntries: Set<@JvmSuppressWildcards NavigatorEntry>,
        ): ViewProvider = MainActivityScreenViewProvider(
            navController = navHostController,
            startDestination = Destination.Transaction.All,
            navigationEntries = navigationEntries
        )

        @Provides
        @MainActivityScreenScope
        fun categoryEditIconUseCase(
            navHostController: NavHostController
        ): CategoryEditIconUseCase = NavControllerCategoryEditIconUseCase(
            navController = navHostController
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionNavigationEntry(
            component: TransactionComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(Destination.Transaction.All) {
            TransactionScreen(
                component = component.logging(logger),
                onTransactionEdit = { navigator.navigateTo(Destination.Transaction.Edit) }
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionEditNavigationEntry(
            componentBuilder: TransactionEditComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(
            destination = Destination.Transaction.Edit,
            attachableViewComponentBuilder = componentBuilder
                .onTransactionSavedHandler { navigator.back() }
                .onEditCategoriesHandler { navigator.navigateTo(Destination.Category.All) }
                .logging(logger)
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryNavigationEntry(
            componentBuilder: CategoryComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(Destination.Category.All) {
            CategoriesScreen(
                component = componentBuilder.logging(logger),
                onCategoriesEdit = { navigator.navigateTo(Destination.Category.Edit) }
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryEditNavigationEntry(
            componentBuilder: CategoryEditComponent.Builder,
            categoryEditIconUseCase: CategoryEditIconUseCase,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(
            destination = Destination.Category.Edit,
            attachableViewComponentBuilder = componentBuilder
                .categoryEditIconUseCase(categoryEditIconUseCase)
                .logging(logger)
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountNavigationEntry(
            componentBuilder: AccountComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(Destination.Account.All) {
            AccountsScreen(
                component = componentBuilder.logging(logger),
                onAccountEdit = { navigator.navigateTo(Destination.Account.Edit) }
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountEditNavigationEntry(
            componentBuilder: AccountEditComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(Destination.Account.Edit) {
            AccountsEditScreen(
                component = componentBuilder
                    .onAccountSavedHandler { navigator.back() }
                    .logging(logger)
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun iconPickerNavigationEntry(
            componentBuilder: IconPickerComponent.Builder,
            categoryEditIconUseCase: CategoryEditIconUseCase,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(
            destination = Destination.Icon.Picker,
            attachableViewComponentBuilder = componentBuilder
                .onIconSelectedHandler { icon ->
                    categoryEditIconUseCase.perform(
                        CategoryEditIconUseCase.Action.Pick(
                            icon = CategoryEditIconUseCase.Icon(
                                id = icon.id,
                                image = icon.image
                            )
                        )
                    )
                }
                .logging(logger)
        )
    }
}