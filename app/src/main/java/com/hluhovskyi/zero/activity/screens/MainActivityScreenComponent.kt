package com.hluhovskyi.zero.activity.screens

import android.content.Context
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.navigation.BottomSheetNavigator
import androidx.navigation.NavHostController
import com.hluhovskyi.zero.BuildConfig
import com.hluhovskyi.zero.R
import com.hluhovskyi.zero.accounts.AccountComponent
import com.hluhovskyi.zero.accounts.detail.AccountDetailComponent
import com.hluhovskyi.zero.accounts.edit.AccountEditComponent
import com.hluhovskyi.zero.accounts.edit.AccountEditCurrencyUseCase
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
import com.hluhovskyi.zero.categories.CategoryType
import com.hluhovskyi.zero.categories.detail.CategoryDetailComponent
import com.hluhovskyi.zero.categories.edit.CategoryEditColorUseCase
import com.hluhovskyi.zero.categories.edit.CategoryEditComponent
import com.hluhovskyi.zero.categories.edit.CategoryEditIconUseCase
import com.hluhovskyi.zero.categories.picker.CategoryPickerComponent
import com.hluhovskyi.zero.colors.ColorPickerComponent
import com.hluhovskyi.zero.common.AttachWithView
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.logging
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.currencies.picker.CurrencyPickerComponent
import com.hluhovskyi.zero.feedback.DeviceInfo
import com.hluhovskyi.zero.feedback.FeedbackComponent
import com.hluhovskyi.zero.feedback.FeedbackService
import com.hluhovskyi.zero.feedback.OnFeedbackSubmittedHandler
import com.hluhovskyi.zero.home.HomeComponent
import com.hluhovskyi.zero.icons.IconPickerComponent
import com.hluhovskyi.zero.imports.ImportComponent
import com.hluhovskyi.zero.settings.SettingsComponent
import com.hluhovskyi.zero.settings.SettingsCurrencyUseCase
import com.hluhovskyi.zero.transactions.DisplayConfig
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.edit.TransactionEditCategoryUseCase
import com.hluhovskyi.zero.transactions.edit.TransactionEditComponent
import com.hluhovskyi.zero.transactions.edit.TransactionEditCurrencyUseCase
import com.hluhovskyi.zero.transactions.filter.TransactionFilterSheetComponent
import com.hluhovskyi.zero.transactions.filter.TransactionFilterUseCase
import com.hluhovskyi.zero.transactions.preview.TransactionPreviewComponent
import com.hluhovskyi.zero.welcome.WelcomeComponent
import dagger.BindsInstance
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    modules = [MainActivityScreenComponent.Module::class],
)
internal abstract class MainActivityScreenComponent : AttachableViewComponent {

    override val tag: String = TAG

    protected abstract val feedbackComponent: FeedbackComponent

    override fun attach(): Closeable = feedbackComponent.attach()

    interface Dependencies {
        val idGenerator: IdGenerator
        val logger: Logger
        val incorrectStateDetector: IncorrectStateDetector

        val context: Context
        val clock: Clock
        val feedbackService: FeedbackService
        val deviceInfo: DeviceInfo

        val bottomBarComponentBuilder: BottomBarComponent.Builder

        val homeComponentBuilder: HomeComponent.Builder
        val welcomeComponentBuilder: WelcomeComponent.Builder
        val transactionComponentBuilder: TransactionComponent.Builder
        val transactionEditComponentBuilder: TransactionEditComponent.Builder
        val transactionPreviewComponentBuilder: TransactionPreviewComponent.Builder

        val categoryComponentBuilder: CategoryComponent.Builder
        val categoryDetailComponentBuilder: CategoryDetailComponent.Builder
        val categoryPickerComponentBuilder: CategoryPickerComponent.Builder
        val categoryEditComponentBuilder: CategoryEditComponent.Builder

        val accountComponentBuilder: AccountComponent.Builder
        val accountEditComponentBuilder: AccountEditComponent.Builder
        val accountDetailComponentBuilder: AccountDetailComponent.Builder

        val currencyPickerComponentBuilder: CurrencyPickerComponent.Builder
        val iconPickerComponentBuilder: IconPickerComponent.Builder
        val transactionFilterSheetComponentBuilder: TransactionFilterSheetComponent.Builder
        val colorPickerComponentFactory: ColorPickerComponent.Factory

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

        @BindsInstance
        fun bottomSheetNavigator(bottomSheetNavigator: BottomSheetNavigator): Builder

        @BindsInstance
        fun modalBottomSheetState(modalBottomSheetState: ModalBottomSheetState): Builder
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
            startDestination = Destinations.Home,
            navController = navHostController,
            logger = logger,
            incorrectStateDetector = incorrectStateDetector,
            navigationArgumentSerializer = navigationArgumentSerializer,
            navigationRouteResolver = navigationRouteResolver,
        )

        @Provides
        @MainActivityScreenScope
        fun navigatorScope(
            navigator: Navigator,
            navigationRouteResolver: NavigationRouteResolver,
        ): NavigatorScope = DefaultNavigatorScope(
            navigator = navigator,
            navigationRouteResolver = navigationRouteResolver,
        )

        @Provides
        @MainActivityScreenScope
        fun viewProvider(
            navHostController: NavHostController,
            bottomSheetNavigator: BottomSheetNavigator,
            modalBottomSheetState: ModalBottomSheetState,
            navigator: Navigator,
            logger: Logger,
            navigationEntries: Set<@JvmSuppressWildcards NavigatorEntry>,
            bottomBarComponent: BottomBarComponent.Builder,
        ): ViewProvider = MainActivityScreenViewProvider(
            navController = navHostController,
            startDestination = Destinations.Home,
            navigationEntries = navigationEntries,
            bottomBar = {
                bottomBarComponent.navigator(navigator)
                    .logging(logger)
                    .AttachWithView()
            },
            bottomSheetNavigator = bottomSheetNavigator,
            modalBottomSheetState = modalBottomSheetState,
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
            inputLogger = logger,
        )

        @Provides
        @MainActivityScreenScope
        fun categoryEditColorUseCase(
            navigator: Navigator,
        ): CategoryEditColorUseCase = DefaultCategoryEditColorUseCase(
            navigator = navigator,
        )

        @Provides
        @MainActivityScreenScope
        fun transactionFilterUseCase(navigator: Navigator): TransactionFilterUseCase = DefaultTransactionFilterUseCase(navigator = navigator)

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun homeNavigationEntry(
            homeComponentBuilder: HomeComponent.Builder,
            welcomeComponentBuilder: WelcomeComponent.Builder,
            transactionComponentBuilder: TransactionComponent.Builder,
            transactionFilterUseCase: TransactionFilterUseCase,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.composable(
            destination = Destinations.Home,
            displayOption = NavigatorEntry.DisplayOption.FullyVisible,
        ) {
            TransactionScreen(
                component = homeComponentBuilder
                    .welcomeComponentBuilder(
                        welcomeComponentBuilder
                            .onImportSelectedHandler { navigator.navigateTo(Destinations.Import) },
                    )
                    .transactionComponentBuilder(
                        transactionComponentBuilder
                            .onTransactionSelectHandler { transactionId ->
                                navigator.navigateTo(
                                    Destinations.Transaction.Item.Edit,
                                    Destinations.Transaction.Item.TransactionId.withValue(transactionId),
                                )
                            }
                            .onAddTransactionHandler { navigator.navigateTo(Destinations.Transaction.Edit) }
                            .transactionFilterUseCase(transactionFilterUseCase)
                            .displayConfig(DisplayConfig(showFab = true)),
                    )
                    .logging(logger),
            )
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionFilterNavigationEntry(
            transactionFilterSheetComponentBuilder: TransactionFilterSheetComponent.Builder,
            transactionFilterUseCase: TransactionFilterUseCase,
            navigatorScope: NavigatorScope,
        ): NavigatorEntry = navigatorScope.buildable(
            destination = Destinations.Transaction.Filter,
            displayOption = NavigatorEntry.DisplayOption.FullyVisible,
        ) {
            transactionFilterSheetComponentBuilder
                .transactionFilterUseCase(transactionFilterUseCase)
        }

        @Provides
        @MainActivityScreenScope
        fun transactionEditCategoryUseCase(
            navigator: Navigator,
            idGenerator: IdGenerator,
        ): TransactionEditCategoryUseCase = DefaultTransactionEditCategoryUseCase(
            navigator = navigator,
            requestIdGenerator = idGenerator,
        )

        @Provides
        @MainActivityScreenScope
        fun transactionEditCurrencyUseCase(
            navigator: Navigator,
            idGenerator: IdGenerator,
        ): TransactionEditCurrencyUseCase = DefaultTransactionEditCurrencyUseCase(
            navigator = navigator,
            requestIdGenerator = idGenerator,
        )

        @Provides
        @MainActivityScreenScope
        fun settingsCurrencyUseCase(
            navigator: Navigator,
            idGenerator: IdGenerator,
        ): SettingsCurrencyUseCase = DefaultSettingsCurrencyUseCase(
            navigator = navigator,
            requestIdGenerator = idGenerator,
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionEditNavigationEntry(
            componentBuilder: TransactionEditComponent.Builder,
            transactionEditCategoryUseCase: TransactionEditCategoryUseCase,
            transactionEditCurrencyUseCase: TransactionEditCurrencyUseCase,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(
            destination = Destinations.Transaction.Edit,
            displayOption = NavigatorEntry.DisplayOption.FullyVisible,
        ) {
            componentBuilder
                .transactionId(Id.Unknown)
                .preSelectedCategoryId(arguments.getValue(Destinations.Transaction.Edit.SelectedCategoryId))
                .preSelectedAccountId(arguments.getValue(Destinations.Transaction.Edit.SelectedAccountId))
                .onTransactionSavedHandler { navigator.back() }
                .onEditCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
                .onDiscardHandler { navigator.back() }
                .transactionEditCategoryUseCase(transactionEditCategoryUseCase)
                .transactionEditCurrencyUseCase(transactionEditCurrencyUseCase)
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun transactionItemEditNavigationEntry(
            componentBuilder: TransactionEditComponent.Builder,
            transactionEditCategoryUseCase: TransactionEditCategoryUseCase,
            transactionEditCurrencyUseCase: TransactionEditCurrencyUseCase,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Transaction.Item.Edit) {
            componentBuilder
                .transactionId(arguments.getValue(Destinations.Transaction.Item.TransactionId))
                .onTransactionSavedHandler { navigator.back() }
                .onEditCategoriesHandler { navigator.navigateTo(Destinations.Category.All) }
                .onDiscardHandler { navigator.back() }
                .transactionEditCategoryUseCase(transactionEditCategoryUseCase)
                .transactionEditCurrencyUseCase(transactionEditCurrencyUseCase)
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
                            Destinations.Category.Item.Detail,
                            Destinations.Category.Item.CategoryId.withValue(categoryId),
                        )
                    }
                    .onAddCategoryHandler { type ->
                        navigator.navigateTo(
                            Destinations.Category.Edit,
                            Destinations.Category.Edit.InitialType.withValue(type.name),
                        )
                    }
                    .logging(logger),
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
            val initialType = CategoryType.from(arguments.getValue(Destinations.Category.Edit.InitialType))
            componentBuilder
                .categoryId(Id.Unknown)
                .initialType(initialType)
                .categoryEditIconUseCase(categoryEditIconUseCase)
                .categoryEditColorUseCase(categoryEditColorUseCase)
                .onCategorySavedHandler { navigator.back() }
                .onDiscardHandler { navigator.back() }
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
                .categoryId(arguments.getValue(Destinations.Category.Item.CategoryId))
                .categoryEditIconUseCase(categoryEditIconUseCase)
                .categoryEditColorUseCase(categoryEditColorUseCase)
                .onCategorySavedHandler { navigator.back() }
                .onDiscardHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryDetailNavigationEntry(
            componentBuilder: CategoryDetailComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Category.Item.Detail) {
            val categoryId = arguments.getValue(Destinations.Category.Item.CategoryId)
            componentBuilder
                .categoryId(categoryId)
                .onBackHandler { navigator.back() }
                .onEditHandler {
                    navigator.navigateTo(
                        Destinations.Category.Item.Edit,
                        Destinations.Category.Item.CategoryId.withValue(categoryId),
                    )
                }
                .onTransactionSelectedHandler { transactionId ->
                    navigator.navigateTo(
                        Destinations.Transaction.Item.Edit,
                        Destinations.Transaction.Item.TransactionId.withValue(transactionId),
                    )
                }
                .onCreateTransactionHandler {
                    navigator.navigateTo(
                        Destinations.Transaction.Edit,
                        Destinations.Transaction.Edit.SelectedCategoryId.withValue(categoryId),
                    )
                }
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
                component = componentBuilder
                    .onAddAccountHandler { navigator.navigateTo(Destinations.Account.Edit) }
                    .onAccountSelectedHandler { accountId ->
                        navigator.navigateTo(
                            Destinations.Account.Item.Detail,
                            Destinations.Account.Item.AccountId.withValue(accountId),
                        )
                    }
                    .onEditAccountHandler { accountId ->
                        navigator.navigateTo(
                            Destinations.Account.Item.Edit,
                            Destinations.Account.Item.AccountId.withValue(accountId),
                        )
                    }
                    .logging(logger),
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
        @MainActivityScreenScope
        internal fun accountEditCurrencyUseCase(
            navigator: Navigator,
            requestIdGenerator: IdGenerator,
        ): AccountEditCurrencyUseCase = DefaultAccountEditCurrencyUseCase(
            navigator = navigator,
            requestIdGenerator = requestIdGenerator,
        )

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountEditNavigationEntry(
            componentBuilder: AccountEditComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
            accountEditIconUseCase: AccountEditIconUseCase,
            accountEditCurrencyUseCase: AccountEditCurrencyUseCase,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Account.Edit) {
            componentBuilder
                .accountEditIconUseCase(accountEditIconUseCase)
                .accountEditCurrencyUseCase(accountEditCurrencyUseCase)
                .onAccountSavedHandler { navigator.back() }
                .onCloseHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountItemEditNavigationEntry(
            componentBuilder: AccountEditComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
            accountEditIconUseCase: AccountEditIconUseCase,
            accountEditCurrencyUseCase: AccountEditCurrencyUseCase,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Account.Item.Edit) {
            componentBuilder
                .accountId(arguments.getValue(Destinations.Account.Item.AccountId))
                .accountEditIconUseCase(accountEditIconUseCase)
                .accountEditCurrencyUseCase(accountEditCurrencyUseCase)
                .onAccountSavedHandler { navigator.back() }
                .onCloseHandler { navigator.back() }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun accountDetailNavigationEntry(
            componentBuilder: AccountDetailComponent.Builder,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Account.Item.Detail) {
            val accountId = arguments.getValue(Destinations.Account.Item.AccountId)
            componentBuilder
                .accountId(accountId)
                .onBackHandler { navigator.back() }
                .onEditHandler {
                    navigator.navigateTo(
                        Destinations.Account.Item.Edit,
                        Destinations.Account.Item.AccountId.withValue(accountId),
                    )
                }
                .onTransactionSelectedHandler { transactionId ->
                    navigator.navigateTo(
                        Destinations.Transaction.Item.Edit,
                        Destinations.Transaction.Item.TransactionId.withValue(transactionId),
                    )
                }
                .onCreateTransactionHandler {
                    navigator.navigateTo(
                        Destinations.Transaction.Edit,
                        Destinations.Transaction.Edit.SelectedAccountId.withValue(accountId),
                    )
                }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun currencyPickerNavigationEntry(
            componentBuilder: CurrencyPickerComponent.Builder,
            accountEditCurrencyUseCase: AccountEditCurrencyUseCase,
            settingsCurrencyUseCase: SettingsCurrencyUseCase,
            transactionEditCurrencyUseCase: TransactionEditCurrencyUseCase,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(
            destination = Destinations.Currency.Picker,
            displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
        ) {
            componentBuilder
                .selectedCurrencyId(arguments.getValue(Destinations.Currency.Picker.SelectedCurrencyId))
                .onCurrencyPickedHandler { currency ->
                    accountEditCurrencyUseCase.perform(
                        AccountEditCurrencyUseCase.Action.Pick(currency),
                    )
                    settingsCurrencyUseCase.perform(
                        SettingsCurrencyUseCase.Action.Pick(currency),
                    )
                    transactionEditCurrencyUseCase.perform(
                        TransactionEditCurrencyUseCase.Action.Pick(currency),
                    )
                }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun categoryPickerNavigationEntry(
            componentBuilder: CategoryPickerComponent.Builder,
            transactionEditCategoryUseCase: TransactionEditCategoryUseCase,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(
            destination = Destinations.Category.Picker,
            displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
        ) {
            componentBuilder
                .selectedCategoryId(arguments.getValue(Destinations.Category.Picker.SelectedCategoryId))
                .onCategorySelectedHandler { categoryId ->
                    transactionEditCategoryUseCase.perform(
                        TransactionEditCategoryUseCase.Action.Pick(categoryId),
                    )
                }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun iconPickerNavigationEntry(
            componentBuilder: IconPickerComponent.Builder,
            navigatorScope: NavigatorScope,
            categoryEditIconUseCase: CategoryEditIconUseCase,
            categoryEditColorUseCase: CategoryEditColorUseCase,
            accountEditIconUseCase: AccountEditIconUseCase,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(
            destination = Destinations.Icon.Picker,
            displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
        ) {
            componentBuilder
                .colorId(arguments.getValue(Destinations.Icon.Picker.ColorId))
                .selectedIconId(arguments.getValue(Destinations.Icon.Picker.SelectedIconId))
                .moneyFirst(arguments.getValue(Destinations.Icon.Picker.MoneyPlacement) != "last")
                .onIconSelectedHandler { icon, colorScheme ->
                    accountEditIconUseCase.perform(
                        AccountEditIconUseCase.Action.Pick(
                            icon = AccountEditIconUseCase.Icon(
                                id = icon.id,
                                image = icon.image,
                            ),
                            colorScheme = colorScheme,
                        ),
                    )
                    categoryEditIconUseCase.perform(
                        CategoryEditIconUseCase.Action.Pick(
                            icon = CategoryEditIconUseCase.Icon(
                                id = icon.id,
                                image = icon.image,
                            ),
                        ),
                    )
                }
                .onColorSelectedHandler { color, colorScheme ->
                    accountEditIconUseCase.perform(
                        AccountEditIconUseCase.Action.PickColor(
                            colorId = color.id,
                            colorScheme = colorScheme,
                        ),
                    )
                    categoryEditColorUseCase.perform(
                        CategoryEditColorUseCase.Action.Pick(
                            color = CategoryEditColorUseCase.Color(
                                id = color.id,
                                color = color.value,
                            ),
                        ),
                    )
                }
                .logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun colorPickerNavigationEntry(
            componentFactory: ColorPickerComponent.Factory,
            navigatorScope: NavigatorScope,
            categoryEditColorUseCase: CategoryEditColorUseCase,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.component(
            destination = Destinations.Color.Picker,
            displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
        ) {
            componentFactory.create(
                onColorSelectedHandler = { color, _ ->
                    categoryEditColorUseCase.perform(
                        CategoryEditColorUseCase.Action.Pick(
                            color = CategoryEditColorUseCase.Color(
                                id = color.id,
                                color = color.value,
                            ),
                        ),
                    )
                },
            ).logging(logger)
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun settingsNavigationEntry(
            componentBuilder: SettingsComponent.Builder,
            settingsCurrencyUseCase: SettingsCurrencyUseCase,
            navigatorScope: NavigatorScope,
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Settings) {
            componentBuilder
                .onImportSelectedHandler { navigator.navigateTo(Destinations.Import) }
                .settingsCurrencyUseCase(settingsCurrencyUseCase)
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
            logger: Logger,
        ): NavigatorEntry = navigatorScope.buildable(Destinations.Transaction.Item.Preview) {
            componentBuilder
                .transactionId(arguments.getValue(Destinations.Transaction.Item.TransactionId))
                .logging(logger)
        }

        @Provides
        @MainActivityScreenScope
        fun feedbackComponent(
            context: Context,
            clock: Clock,
            navigator: Navigator,
            feedbackService: FeedbackService,
            deviceInfo: DeviceInfo,
        ): FeedbackComponent {
            val dependencies = object : FeedbackComponent.Dependencies {
                override val context: Context = context
                override val clock: Clock = clock
                override val feedbackService: FeedbackService = feedbackService
                override val deviceInfo: DeviceInfo = deviceInfo
                override val routes: Flow<String> = navigator.state.map { it.destination.route }
                override val onShakeDetected: () -> Unit = {
                    navigator.navigateTo(Destinations.Feedback)
                }
                override val isDebugBuild: Boolean = BuildConfig.DEBUG
                override val errorMessageProvider: () -> String = {
                    context.getString(R.string.feedback_error_generic)
                }
                override val onFeedbackSubmittedHandler: OnFeedbackSubmittedHandler =
                    OnFeedbackSubmittedHandler { navigator.back() }
            }
            return FeedbackComponent.builder(dependencies).build()
        }

        @Provides
        @IntoSet
        @MainActivityScreenScope
        fun feedbackNavigationEntry(
            feedbackComponent: FeedbackComponent,
            navigatorScope: NavigatorScope,
        ): NavigatorEntry = navigatorScope.component(
            destination = Destinations.Feedback,
            displayOption = NavigatorEntry.DisplayOption.PartiallyVisible.BottomSheet,
        ) {
            feedbackComponent.sheetComponentProvider.get()
        }
    }
}
