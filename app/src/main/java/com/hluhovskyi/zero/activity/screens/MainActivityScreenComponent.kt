package com.hluhovskyi.zero.activity.screens

import androidx.navigation.NavHostController
import com.hluhovskyi.zero.accounts.AccountComponent
import com.hluhovskyi.zero.accounts.edit.AccountEditComponent
import com.hluhovskyi.zero.accounts.edit.AccountEditIconUseCase
import com.hluhovskyi.zero.activity.navigation.DefaultNavigatorScope
import com.hluhovskyi.zero.activity.navigation.Destinations
import com.hluhovskyi.zero.activity.navigation.NavControllerNavigator
import com.hluhovskyi.zero.activity.navigation.Navigator
import com.hluhovskyi.zero.activity.navigation.NavigatorEntry
import com.hluhovskyi.zero.activity.navigation.NavigatorScope
import com.hluhovskyi.zero.activity.navigation.back
import com.hluhovskyi.zero.activity.navigation.getValue
import com.hluhovskyi.zero.activity.navigation.navigateTo
import com.hluhovskyi.zero.activity.navigation.route.DefaultNavigationRouteResolver
import com.hluhovskyi.zero.activity.navigation.route.NavigationRouteResolver
import com.hluhovskyi.zero.activity.navigation.serialization.CompositeNavigationArgumentSerializer
import com.hluhovskyi.zero.activity.navigation.serialization.NavigationArgumentSerializer
import com.hluhovskyi.zero.activity.navigation.withValue
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
import com.hluhovskyi.zero.common.Id
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
import com.hluhovskyi.zero.transactions.preview.TransactionPreviewComponent
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
        val transactionPreviewComponentBuilder: TransactionPreviewComponent.Builder

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
        fun navigationArgumentSerializer(): NavigationArgumentSerializer = CompositeNavigationArgumentSerializer()

        @Provides
        @MainActivityScreenScope
        fun navigationRouteResolver(
            incorrectStateDetector: IncorrectStateDetector,
            navigationArgumentSerializer: NavigationArgumentSerializer,
        ): NavigationRouteResolver = DefaultNavigationRouteResolver(
            incorrectStateDetector = incorrectStateDetector,
            navigationArgumentSerializer = navigationArgumentSerializer,
        )

        @Provides
        @MainActivityScreenScope
        fun navigator(
            navHostController: NavHostController,
            logger: Logger,
            incorrectStateDetector: IncorrectStateDetector,
            navigationArgumentSerializer: NavigationArgumentSerializer,
            navigationRouteResolver: NavigationRouteResolver,
        ): Navigator = NavControllerNavigator(
            startDestination = Destinations.Transaction.All,
            navController = navHostController,
            logger = logger,
            incorrectStateDetector = incorrectStateDetector,
            navigationArgumentSerializer = navigationArgumentSerializer,
            navigationRouteResolver = navigationRouteResolver
        )

        @Provides
        @MainActivityScreenScope
        fun navigatorScope(
            navigator: Navigator,
            navigationRouteResolver: NavigationRouteResolver,
        ): NavigatorScope = DefaultNavigatorScope(
            navigator = navigator,
            navigationRouteResolver = navigationRouteResolver
        )

        @Provides
        @MainActivityScreenScope
        fun viewProvider(
            navHostController: NavHostController,
            navigator: Navigator,
            logger: Logger,
            navigationEntries: Set<@JvmSuppressWildcards NavigatorEntry>,
            bottomBarComponent: BottomBarComponent.Builder
        ): ViewProvider = MainActivityScreenViewProvider(
            navController = navHostController,
            startDestination = Destinations.Transaction.All,
            navigationEntries = navigationEntries,
            bottomBar = {
                bottomBarComponent.navigator(navigator)
                    .logging(logger)
                    .AttachWithView()
            }
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
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.composable(
            destination = Destinations.Transaction.All,
            displayOption = NavigatorEntry.DisplayOption.FullyVisible,
        ) {
            TransactionScreen(
                component = component
                    .onTransactionSelectHandler { transactionId ->
                        navigator.navigateTo(
                            Destinations.Transaction.Item.Edit,
                            Destinations.Transaction.Item.TransactionId.withValue(transactionId)
                        )
                    }
                    .logging(logger),
                onTransactionEdit = { navigator.navigateTo(Destinations.Transaction.Edit) }
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionEditNavigationEntry(
            componentBuilder: TransactionEditComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(
            destination = Destinations.Transaction.Edit,
            displayOption = NavigatorEntry.DisplayOption.FullyVisible,
        ) {
            componentBuilder
                .transactionId(Id.Unknown)
                .onTransactionSavedHandler { navigator.back() }
                .onEditCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
                .onDiscardHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionItemEditNavigationEntry(
            componentBuilder: TransactionEditComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Transaction.Item.Edit) {
            componentBuilder
                .transactionId(arguments.getValue(Destinations.Transaction.Item.TransactionId))
                .onTransactionSavedHandler { navigator.back() }
                .onEditCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
                .onDiscardHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryNavigationEntry(
            componentBuilder: CategoryComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.composable(Destinations.Category.All) {
            CategoriesScreen(
                component = componentBuilder
                    .onCategorySelectedHandler { categoryId ->
                        navigator.navigateTo(
                            Destinations.Category.Item.Edit,
                            Destinations.Category.Item.Edit.CategoryId.withValue(categoryId)
                        )
                    }
                    .logging(logger),
                onCategoriesEdit = { navigator.navigateTo(Destinations.Category.Edit) }
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryEditNavigationEntry(
            componentBuilder: CategoryEditComponent.Builder,
            navigatorScope: NavigatorScope,
            categoryEditIconUseCase: CategoryEditIconUseCase,
            categoryEditColorUseCase: CategoryEditColorUseCase,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Category.Edit) {
            componentBuilder
                .categoryId(Id.Unknown)
                .categoryEditIconUseCase(categoryEditIconUseCase)
                .categoryEditColorUseCase(categoryEditColorUseCase)
                .onCategorySavedHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryEditItemNavigationEntry(
            componentBuilder: CategoryEditComponent.Builder,
            navigatorScope: NavigatorScope,
            categoryEditIconUseCase: CategoryEditIconUseCase,
            categoryEditColorUseCase: CategoryEditColorUseCase,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Category.Item.Edit) {
            componentBuilder
                .categoryId(arguments.getValue(Destinations.Category.Item.Edit.CategoryId))
                .categoryEditIconUseCase(categoryEditIconUseCase)
                .categoryEditColorUseCase(categoryEditColorUseCase)
                .onCategorySavedHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountNavigationEntry(
            componentBuilder: AccountComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.composable(Destinations.Account.All) {
            AccountsScreen(
                component = componentBuilder.logging(logger),
                onAccountEdit = { navigator.navigateTo(Destinations.Account.Edit) }
            )
        }

        @Provides
        @MainActivityScreenScope
        internal fun accountEditIconUseCase(
            navigator: Navigator,
            requestIdGenerator: IdGenerator,
            logger: Logger,
        ): AccountEditIconUseCase = DefaultAccountEditIconUseCase(
            navigator = navigator,
            requestIdGenerator = requestIdGenerator,
            inputLogger = logger,
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountEditNavigationEntry(
            componentBuilder: AccountEditComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
            accountEditIconUseCase: AccountEditIconUseCase,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Account.Edit) {
            componentBuilder
                .accountEditIconUseCase(accountEditIconUseCase)
                .onAccountSavedHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun iconPickerNavigationEntry(
            componentBuilder: IconPickerComponent.Builder,
            navigatorScope: NavigatorScope,
            categoryEditIconUseCase: CategoryEditIconUseCase,
            accountEditIconUseCase: AccountEditIconUseCase,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Icon.Picker) {
            componentBuilder
                .colorId(arguments.getValue(Destinations.Icon.Picker.ColorId))
                .onIconSelectedHandler { icon ->
                    accountEditIconUseCase.perform(
                        AccountEditIconUseCase.Action.Pick(
                            icon = AccountEditIconUseCase.Icon(
                                id = icon.id,
                                image = icon.image,
                            )
                        )
                    )
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
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun colorPickerNavigationEntry(
            componentBuilder: ColorPickerComponent.Builder,
            navigatorScope: NavigatorScope,
            categoryEditColorUseCase: CategoryEditColorUseCase,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Color.Picker) {
            componentBuilder
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
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun settingsNavigationEntry(
            componentBuilder: SettingsComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Settings) {
            componentBuilder
                .onImportSelectedHandler { navigator.navigateTo(Destinations.Import) }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun importNavigationEntry(
            componentBuilder: ImportComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Import) {
            componentBuilder
                .onImportFinishedHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionPreviewEntry(
            componentBuilder: TransactionPreviewComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Transaction.Item.Preview) {
            componentBuilder
                .transactionId(arguments.getValue(Destinations.Transaction.Item.TransactionId))
                .logging(logger)
        }
    }
}