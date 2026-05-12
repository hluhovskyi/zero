package com.hluhovskyi.zero.presets

import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.common.Attachable
import com.hluhovskyi.zero.common.Buildable
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyPrimaryUseCase
import dagger.BindsInstance
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class PresetsScope

@PresetsScope
@dagger.Component(
    modules = [PresetsComponent.Module::class],
    dependencies = [PresetsComponent.Dependencies::class],
)
abstract class PresetsComponent {

    abstract val attachable: Attachable

    interface Dependencies {
        val categoryRepository: CategoryRepository
        val accountRepository: AccountRepository
        val currencyPrimaryUseCase: CurrencyPrimaryUseCase
        val configurationRepository: ConfigurationRepository
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerPresetsComponent.builder()
            .dependencies(dependencies)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<PresetsComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun coroutineScope(scope: CoroutineScope): Builder
    }

    @dagger.Module
    object Module {

        @Provides
        @PresetsScope
        fun presetsUseCase(
            categoryRepository: CategoryRepository,
            accountRepository: AccountRepository,
            currencyPrimaryUseCase: CurrencyPrimaryUseCase,
            configurationRepository: ConfigurationRepository,
        ): PresetsUseCase = DefaultPresetsUseCase(
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            currencyPrimaryUseCase = currencyPrimaryUseCase,
            configurationRepository = configurationRepository,
        )

        @Provides
        @PresetsScope
        fun attachable(presetsUseCase: PresetsUseCase, coroutineScope: CoroutineScope): Attachable =
            PresetsAttachable(presetsUseCase, coroutineScope)
    }
}

private class PresetsAttachable(
    private val presetsUseCase: PresetsUseCase,
    private val coroutineScope: CoroutineScope,
) : Attachable {

    override fun attach(): Closeable = Closeables.of {
        coroutineScope.launch { presetsUseCase.seed() }
    }
}
