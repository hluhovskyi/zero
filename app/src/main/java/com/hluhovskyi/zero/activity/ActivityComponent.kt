package com.hluhovskyi.zero.activity

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountComponent
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.AccountsQueryUseCase
import com.hluhovskyi.zero.accounts.detail.AccountDetailComponent
import com.hluhovskyi.zero.accounts.edit.AccountEditComponent
import com.hluhovskyi.zero.activity.screens.MainActivityScreenComponent
import com.hluhovskyi.zero.activity.screens.bottombar.BottomBarComponent
import com.hluhovskyi.zero.analytics.AnalyticsComponent
import com.hluhovskyi.zero.analytics.AnalyticsDetailComponent
import com.hluhovskyi.zero.analytics.breakdown.SpendingBreakdownComponent
import com.hluhovskyi.zero.backup.BackupDetailComponent
import com.hluhovskyi.zero.budget.BudgetComponent
import com.hluhovskyi.zero.budget.BudgetQueryUseCase
import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.budget.edit.BudgetEditComponent
import com.hluhovskyi.zero.budget.over.BudgetOverComponent
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryComponent
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.detail.CategoryDetailComponent
import com.hluhovskyi.zero.categories.edit.CategoryEditComponent
import com.hluhovskyi.zero.categories.picker.CategoryPickerComponent
import com.hluhovskyi.zero.colors.ColorPickerComponent
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AmountFormatter
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.DateFormatter
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.coroutines.DispatcherProvider
import com.hluhovskyi.zero.common.merge
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.common.time.ZoneProvider
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyConvertUseCase
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.currencies.picker.CurrencyPickerComponent
import com.hluhovskyi.zero.feedback.DeviceInfo
import com.hluhovskyi.zero.feedback.FeedbackService
import com.hluhovskyi.zero.home.HomeComponent
import com.hluhovskyi.zero.icons.IconPickerComponent
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.imports.ImportComponent
import com.hluhovskyi.zero.presets.PresetsComponent
import com.hluhovskyi.zero.security.BiometricAuthenticator
import com.hluhovskyi.zero.security.BiometricLockComponent
import com.hluhovskyi.zero.security.BiometricLockUseCase
import com.hluhovskyi.zero.settings.SettingsComponent
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.breakdown.SpendingBreakdownUseCase
import com.hluhovskyi.zero.transactions.edit.TransactionEditComponent
import com.hluhovskyi.zero.transactions.filter.TransactionFilterSheetComponent
import com.hluhovskyi.zero.transactions.preview.TransactionPreviewComponent
import com.hluhovskyi.zero.welcome.WelcomeComponent
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class ActivityScope

private const val TAG = "ActivityComponent"

@ActivityScope
@dagger.Component(
    modules = [ActivityComponent.Module::class],
    dependencies = [ActivityComponent.Dependencies::class],
)
abstract class ActivityComponent :
    AttachableViewComponent,
    BottomBarComponent.Dependencies,
    MainActivityScreenComponent.Dependencies,
    AccountComponent.Dependencies,
    AccountEditComponent.Dependencies,
    AccountDetailComponent.Dependencies,
    BudgetComponent.Dependencies,
    BudgetEditComponent.Dependencies,
    BudgetOverComponent.Dependencies,
    CategoryComponent.Dependencies,
    CategoryDetailComponent.Dependencies,
    CategoryPickerComponent.Dependencies,
    CurrencyPickerComponent.Dependencies,
    CategoryEditComponent.Dependencies,
    AnalyticsComponent.Dependencies,
    AnalyticsDetailComponent.Dependencies,
    HomeComponent.Dependencies,
    WelcomeComponent.Dependencies,
    TransactionComponent.Dependencies,
    SpendingBreakdownComponent.Dependencies,
    TransactionEditComponent.Dependencies,
    TransactionPreviewComponent.Dependencies,
    IconPickerComponent.Dependencies,
    ColorPickerComponent.Dependencies,
    TransactionFilterSheetComponent.Dependencies,
    BiometricLockComponent.Dependencies {

    override val tag: String = TAG

    protected abstract val attachActivityComponent: Attachable

    override fun attach(): Closeable = attachActivityComponent.attach()

    interface Dependencies {

        val context: Context

        val dispatcherProvider: DispatcherProvider
        val clock: Clock
        val zoneProvider: ZoneProvider
        val zonedClock: ZonedClock
        val imageLoader: ImageLoader
        val amountFormatter: AmountFormatter
        val dateFormatter: DateFormatter
        val androidUriResourceFactory: AndroidUriResourceFactory
        val incorrectStateDetector: IncorrectStateDetector

        val categoriesQueryUseCase: CategoriesQueryUseCase
        val accountsQueryUseCase: AccountsQueryUseCase
        val budgetQueryUseCase: BudgetQueryUseCase
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val currencyConvertUseCase: CurrencyConvertUseCase

        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val categoryRepository: CategoryRepository
        val budgetRepository: BudgetRepository
        val transactionRepository: TransactionRepository
        val iconRepository: IconRepository
        val colorRepository: ColorRepository
        val configurationRepository: ConfigurationRepository

        val importComponentBuilder: ImportComponent.Builder
        val settingsComponentBuilder: SettingsComponent.Builder
        val backupDetailComponentBuilder: BackupDetailComponent.Builder
        val presetsComponent: PresetsComponent

        val feedbackService: FeedbackService
        val deviceInfo: DeviceInfo
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerActivityComponent.builder()
            .dependencies(dependencies)
            .logger(Logger.Noop)
            .idGenerator(IdGenerator.UUID)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<ActivityComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun logger(logger: Logger): Builder

        @BindsInstance
        fun idGenerator(idGenerator: IdGenerator): Builder

        @BindsInstance
        fun activity(activity: FragmentActivity): Builder
    }

    @dagger.Module(
        includes = [MainActivityModule::class],
    )
    object Module {

        @Provides
        fun accountComponentBuilder(
            component: ActivityComponent,
        ): AccountComponent.Builder = AccountComponent.builder(component)

        @Provides
        fun accountEditComponentBuilder(
            component: ActivityComponent,
        ): AccountEditComponent.Builder = AccountEditComponent.builder(component)

        @Provides
        fun accountDetailComponentBuilder(
            component: ActivityComponent,
        ): AccountDetailComponent.Builder = AccountDetailComponent.builder(component)

        @Provides
        fun categoryComponentBuilder(
            component: ActivityComponent,
        ): CategoryComponent.Builder = CategoryComponent.builder(component)

        @Provides
        @ActivityScope
        fun analyticsComponent(
            component: ActivityComponent,
        ): AnalyticsComponent = AnalyticsComponent.create(component)

        @Provides
        @ActivityScope
        fun spendingBreakdownUseCase(
            analyticsComponent: AnalyticsComponent,
        ): SpendingBreakdownUseCase = analyticsComponent.spendingBreakdownUseCase

        @Provides
        fun analyticsDetailComponentBuilder(
            component: ActivityComponent,
        ): AnalyticsDetailComponent.Builder = AnalyticsDetailComponent.builder(component)

        @Provides
        fun budgetComponentBuilder(
            component: ActivityComponent,
        ): BudgetComponent.Builder = BudgetComponent.builder(component)

        @Provides
        fun budgetEditComponentBuilder(
            component: ActivityComponent,
        ): BudgetEditComponent.Builder = BudgetEditComponent.builder(component)

        @Provides
        fun budgetOverComponentBuilder(
            component: ActivityComponent,
        ): BudgetOverComponent.Builder = BudgetOverComponent.builder(component)

        @Provides
        fun categoryPickerComponentBuilder(
            component: ActivityComponent,
        ): CategoryPickerComponent.Builder = CategoryPickerComponent.builder(component)

        @Provides
        fun currencyPickerComponentBuilder(
            component: ActivityComponent,
        ): CurrencyPickerComponent.Builder = CurrencyPickerComponent.builder(component)

        @Provides
        fun categoryEditComponentBuilder(
            component: ActivityComponent,
        ): CategoryEditComponent.Builder = CategoryEditComponent.builder(component)

        @Provides
        fun transactionEditComponentBuilder(
            component: ActivityComponent,
        ): TransactionEditComponent.Builder = TransactionEditComponent.builder(component)

        @Provides
        fun transactionComponentBuilder(
            component: ActivityComponent,
        ): TransactionComponent.Builder = TransactionComponent.builder(component)

        @Provides
        fun welcomeComponentBuilder(
            component: ActivityComponent,
        ): WelcomeComponent.Builder = WelcomeComponent.builder(component)

        @Provides
        fun homeComponentBuilder(
            component: ActivityComponent,
        ): HomeComponent.Builder = HomeComponent.builder(component)

        @Provides
        fun categoryDetailComponentBuilder(
            component: ActivityComponent,
        ): CategoryDetailComponent.Builder = CategoryDetailComponent.builder(component)

        @Provides
        fun spendingBreakdownComponentBuilder(
            component: ActivityComponent,
        ): SpendingBreakdownComponent.Builder = SpendingBreakdownComponent.builder(component)

        @Provides
        fun iconPickerComponentBuilder(
            component: ActivityComponent,
        ): IconPickerComponent.Builder = IconPickerComponent.builder(component)

        @Provides
        fun transactionFilterSheetComponentBuilder(
            component: ActivityComponent,
        ): TransactionFilterSheetComponent.Builder = TransactionFilterSheetComponent.builder(component)

        @Provides
        @ActivityScope
        fun colorPickerComponentFactory(
            component: ActivityComponent,
        ): ColorPickerComponent.Factory = ColorPickerComponent.factory(component)

        @Provides
        fun transactionPreviewBuilder(
            component: ActivityComponent,
        ): TransactionPreviewComponent.Builder = TransactionPreviewComponent.builder(component)

        @Provides
        @ActivityScope
        fun attachActivityComponent(
            presetsComponent: PresetsComponent,
            biometricLockComponent: BiometricLockComponent,
            attachJankStatsToActivity: AttachJankStatsToActivity,
        ): Attachable = Attachable.merge(
            AttachActivityComponent(
                presetsComponent = presetsComponent,
                biometricLockComponent = biometricLockComponent,
            ),
            attachJankStatsToActivity,
        )

        @Provides
        @ActivityScope
        fun attachJankStatsToActivity(
            fragmentActivity: FragmentActivity,
            logger: Logger,
        ): AttachJankStatsToActivity = AttachJankStatsToActivity(
            activity = fragmentActivity,
            logger = logger,
        )

        @Provides
        @ActivityScope
        fun biometricLockComponent(
            component: ActivityComponent,
            activity: FragmentActivity,
        ): BiometricLockComponent = BiometricLockComponent.builder(component)
            .activity(activity)
            .build()

        @Provides
        @ActivityScope
        fun biometricLockUseCase(
            biometricLockComponent: BiometricLockComponent,
        ): BiometricLockUseCase = biometricLockComponent.biometricLockUseCase

        @Provides
        @ActivityScope
        fun biometricAuthenticator(
            biometricLockComponent: BiometricLockComponent,
        ): BiometricAuthenticator = biometricLockComponent.biometricAuthenticator
    }
}

@dagger.Module
internal object MainActivityModule {

    @Provides
    @ActivityScope
    fun viewProvider(
        screenComponent: MainActivityScreenComponent.Builder,
        biometricLockComponent: BiometricLockComponent,
    ): ViewProvider = MainActivityViewProvider(
        screenComponent = screenComponent,
        biometricLockGateComponent = biometricLockComponent.gateComponent,
    )

    @Provides
    fun bottomBarComponentBuilder(
        component: ActivityComponent,
    ): BottomBarComponent.Builder = BottomBarComponent.builder(component)

    @Provides
    fun mainActivityScreenComponentBuilder(
        component: ActivityComponent,
    ): MainActivityScreenComponent.Builder = MainActivityScreenComponent.builder(component)
}
