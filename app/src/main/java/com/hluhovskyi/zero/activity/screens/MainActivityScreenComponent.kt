package com.hluhovskyi.zero.activity.screens

import androidx.navigation.NavHostController
import com.hluhovskyi.zero.accounts.AccountComponent
import com.hluhovskyi.zero.accounts.edit.AccountEditComponent
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.NavControllerNavigator
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.activity.screens.bottombar.BottomBarComponent
import com.hluhovskyi.zero.categories.CategoryComponent
import com.hluhovskyi.zero.categories.edit.CategoryEditColorUseCase
import com.hluhovskyi.zero.categories.edit.CategoryEditComponent
import com.hluhovskyi.zero.categories.edit.CategoryEditIconUseCase
import com.hluhovskyi.zero.colors.ColorPickerComponent
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.logging
import com.hluhovskyi.zero.icons.IconPickerComponent
import com.hluhovskyi.zero.imports.ImportComponent
import com.hluhovskyi.zero.settings.SettingsComponent
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
        val idGenerator: IdGenerator
        val logger: Logger
        val incorrectStateDetector: IncorrectStateDetector

        val bottomBarComponentBuilder: BottomBarComponent.Builder

        val transactionComponentBuilder: TransactionComponent.Builder
        val transactionEditComponentBuilder: TransactionEditComponent.Builder

        val categoryComponentBuilder: CategoryComponent.Builder
        val categoryEditComponentBuilder: CategoryEditComponent.Builder

        val accountComponentBuilder: AccountComponent.Builder
        val accountEditComponentBuilder: AccountEditComponent.Builder

        val iconPickerComponentBuilder: IconPickerComponent.Builder
        val colorPickerComponentBuilder: ColorPickerComponent.Builder

        val settingsComponentBuilder: SettingsComponent.Builder
        val importComponentBuilder: ImportComponent.Builder
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
            navHostController: NavHostController,
            logger: Logger,
            incorrectStateDetector: IncorrectStateDetector,
        ): Navigator = NavControllerNavigator(
            startDestination = Destinations.Transaction.All,
            navController = navHostController,
            logger = logger,
            incorrectStateDetector = incorrectStateDetector
        )

        @Provides
        @MainActivityScreenScope
        fun viewProvider(
            navHostController: NavHostController,
            navigator: Navigator,
            navigationEntries: Set<@JvmSuppressWildcards NavigatorEntry>,
            bottomBarComponent: BottomBarComponent.Builder
        ): ViewProvider = MainActivityScreenViewProvider(
            navController = navHostController,
            navigator = navigator,
            startDestination = Destinations.Transaction.All,
            navigationEntries = navigationEntries,
            bottomBar = { bottomBarComponent.navigator(navigator).AttachWithView() }
        )

        @Provides
        @MainActivityScreenScope
        fun categoryEditIconUseCase(
            navigator: Navigator,
            idGenerator: IdGenerator,
            logger: Logger,
        ): CategoryEditIconUseCase = DefaultCategoryEditIconUseCase(
            navigator = navigator,
            requestIdGenerator = idGenerator,
            inputLogger = logger
        )

        @Provides
        @MainActivityScreenScope
        fun categoryEditColorUseCase(
            navigator: Navigator,
            idGenerator: IdGenerator,
            logger: Logger,
        ): CategoryEditColorUseCase = DefaultCategoryEditColorUseCase(
            navigator = navigator,
            requestIdGenerator = idGenerator,
            logger
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionNavigationEntry(
            component: TransactionComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(Destinations.Transaction.All) {
            TransactionScreen(
                component = component.logging(logger),
                onTransactionEdit = { navigator.navigateTo(Destinations.Transaction.Edit) }
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
            destination = Destinations.Transaction.Edit,
            attachableViewComponentBuilder = componentBuilder
                .onTransactionSavedHandler { navigator.back() }
                .onEditCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
                .logging(logger)
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryNavigationEntry(
            componentBuilder: CategoryComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(Destinations.Category.All) {
            CategoriesScreen(
                component = componentBuilder.logging(logger),
                onCategoriesEdit = { navigator.navigateTo(Destinations.Category.Edit) }
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryEditNavigationEntry(
            componentBuilder: CategoryEditComponent.Builder,
            categoryEditIconUseCase: CategoryEditIconUseCase,
            categoryEditColorUseCase: CategoryEditColorUseCase,
            logger: Logger,
            navigator: Navigator,
        ): NavigatorEntry = navigationEntryOf(
            destination = Destinations.Category.Edit,
            attachableViewComponentBuilder = componentBuilder
                .categoryEditIconUseCase(categoryEditIconUseCase)
                .categoryEditColorUseCase(categoryEditColorUseCase)
                .onCategorySavedHandler { navigator.back() }
                .logging(logger)
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountNavigationEntry(
            componentBuilder: AccountComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(Destinations.Account.All) {
            AccountsScreen(
                component = componentBuilder.logging(logger),
                onAccountEdit = { navigator.navigateTo(Destinations.Account.Edit) }
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountEditNavigationEntry(
            componentBuilder: AccountEditComponent.Builder,
            navigator: Navigator,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(Destinations.Account.Edit) {
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
            destination = Destinations.Icon.Picker,
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

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun colorPickerNavigationEntry(
            componentBuilder: ColorPickerComponent.Builder,
            categoryEditColorUseCase: CategoryEditColorUseCase,
            logger: Logger,
        ): NavigatorEntry = navigationEntryOf(
            destination = Destinations.Color.Picker,
            attachableViewComponentBuilder = componentBuilder
                .onColorSelectedHandler { color ->
                    categoryEditColorUseCase.perform(
                        CategoryEditColorUseCase.Action.Pick(
                            color = CategoryEditColorUseCase.Color(
                                id = color.id,
                                color = color.value,
                            )
                        )
                    )
                }
                .logging(logger)
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun settingsNavigationEntry(
            componentBuilder: SettingsComponent.Builder,
            logger: Logger,
            navigator: Navigator,
        ): NavigatorEntry = navigationEntryOf(
            destination = Destinations.Settings,
            attachableViewComponentBuilder = componentBuilder
                .onImportSelectedHandler { navigator.navigateTo(Destinations.Import) }
                .logging(logger)
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun importNavigationEntry(
            componentBuilder: ImportComponent.Builder,
            logger: Logger
        ): NavigatorEntry = navigationEntryOf(
            destination = Destinations.Import,
            attachableViewComponentBuilder = componentBuilder.logging(logger)
        )
    }
}