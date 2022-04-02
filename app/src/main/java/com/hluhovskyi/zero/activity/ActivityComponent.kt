package com.hluhovskyi.zero.activity

import com.hluhovskyi.zero.ImageLoader
import com.hluhovskyi.zero.accounts.AccountComponent
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.edit.AccountEditComponent
import com.hluhovskyi.zero.activity.screens.MainActivityScreenComponent
import com.hluhovskyi.zero.activity.screens.bottombar.BottomBarComponent
import com.hluhovskyi.zero.categories.CategoriesQueryUseCase
import com.hluhovskyi.zero.categories.CategoryComponent
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.edit.CategoryEditComponent
import com.hluhovskyi.zero.colors.ColorPickerComponent
import com.hluhovskyi.zero.colors.ColorRepository
import com.hluhovskyi.zero.common.AndroidUriResourceFactory
import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.ViewProvider
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.icons.IconPickerComponent
import com.hluhovskyi.zero.icons.IconRepository
import com.hluhovskyi.zero.imports.ImportComponent
import com.hluhovskyi.zero.imports.ImportSourceUseCase
import com.hluhovskyi.zero.settings.SettingsComponent
import com.hluhovskyi.zero.transactions.TransactionComponent
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.transactions.edit.TransactionEditComponent
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
    dependencies = [ActivityComponent.Dependencies::class]
)
abstract class ActivityComponent :
    AttachableViewComponent,
    BottomBarComponent.Dependencies,
    MainActivityScreenComponent.Dependencies,
    AccountComponent.Dependencies,
    AccountEditComponent.Dependencies,
    CategoryComponent.Dependencies,
    CategoryEditComponent.Dependencies,
    TransactionComponent.Dependencies,
    TransactionEditComponent.Dependencies,
    IconPickerComponent.Dependencies,
    ColorPickerComponent.Dependencies,
    SettingsComponent.Dependencies,
    ImportComponent.Dependencies {

    override val tag: String = TAG
    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {

        val clock: Clock
        val imageLoader: ImageLoader
        val androidUriResourceFactory: AndroidUriResourceFactory
        val incorrectStateDetector: IncorrectStateDetector

        val categoriesQueryUseCase: CategoriesQueryUseCase
        val importSourceUseCase: ImportSourceUseCase

        val accountRepository: AccountRepository
        val currencyRepository: CurrencyRepository
        val categoryRepository: CategoryRepository
        val transactionRepository: TransactionRepository
        val iconRepository: IconRepository
        val colorRepository: ColorRepository
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
    }

    @dagger.Module(
        includes = [MainActivityModule::class]
    )
    object Module {

        @Provides
        @ActivityScope
        fun accountComponentBuilder(
            component: ActivityComponent,
            accountRepository: AccountRepository,
            transactionRepository: TransactionRepository,
        ): AccountComponent.Builder = AccountComponent.builder(component)
            .accountRepository(accountRepository)
            .transactionRepository(transactionRepository)

        @Provides
        @ActivityScope
        fun accountEditComponentBuilder(
            component: ActivityComponent,
            idGenerator: IdGenerator,
            currencyRepository: CurrencyRepository,
            accountRepository: AccountRepository
        ): AccountEditComponent.Builder = AccountEditComponent.builder(component)
            .idGenerator(idGenerator)
            .currencyRepository(currencyRepository)
            .accountRepository(accountRepository)

        @Provides
        @ActivityScope
        fun categoryComponentBuilder(
            component: ActivityComponent
        ): CategoryComponent.Builder = CategoryComponent.builder(component)

        @Provides
        @ActivityScope
        fun categoryEditComponentBuilder(
            component: ActivityComponent,
        ): CategoryEditComponent.Builder = CategoryEditComponent.builder(component)

        @Provides
        @ActivityScope
        fun transactionEditComponentBuilder(
            component: ActivityComponent,
        ): TransactionEditComponent.Builder = TransactionEditComponent.builder(component)

        @Provides
        @ActivityScope
        fun transactionComponentBuilder(
            component: ActivityComponent,
        ): TransactionComponent.Builder = TransactionComponent.builder(component)

        @Provides
        @ActivityScope
        fun iconPickerComponentBuilder(
            component: ActivityComponent,
        ): IconPickerComponent.Builder = IconPickerComponent.builder(component)

        @Provides
        @ActivityScope
        fun colorPickerComponentBuilder(
            component: ActivityComponent
        ): ColorPickerComponent.Builder = ColorPickerComponent.builder(component)

        @Provides
        @ActivityScope
        fun settingsComponentBuilder(
            component: ActivityComponent
        ): SettingsComponent.Builder = SettingsComponent.builder(component)

        @Provides
        @ActivityScope
        fun importComponentBuilder(
            component: ActivityComponent,
            importSourceUseCase: ImportSourceUseCase,
        ): ImportComponent.Builder = ImportComponent.builder(component)
            .importSourceUseCase(importSourceUseCase)
    }
}

@dagger.Module
private object MainActivityModule {

    @Provides
    @ActivityScope
    fun viewProvider(
        screenComponent: MainActivityScreenComponent.Builder,
    ): ViewProvider = MainActivityViewProvider(
        screenComponent = screenComponent
    )

    @Provides
    @ActivityScope
    fun bottomBarComponentBuilder(
        component: ActivityComponent
    ): BottomBarComponent.Builder = BottomBarComponent.builder(component)

    @Provides
    @ActivityScope
    fun mainActivityScreenComponentBuilder(
        component: ActivityComponent,
    ): MainActivityScreenComponent.Builder = MainActivityScreenComponent.builder(component)
}