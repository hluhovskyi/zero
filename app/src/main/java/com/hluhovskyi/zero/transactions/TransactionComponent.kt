package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Closeables
import com.hluhovskyi.zero.common.ViewProvider
import dagger.BindsInstance
import dagger.Provides
import java.io.Closeable
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class TransactionScope

@TransactionScope
@dagger.Component(
    modules = [TransactionComponent.Module::class],
    dependencies = [TransactionComponent.Dependencies::class]
)
abstract class TransactionComponent : AttachableViewComponent {

    override fun attach(): Closeable = Closeables.empty()

    interface Dependencies {

    }

    companion object {

        fun factory(): Factory = DaggerTransactionComponent.factory()
    }

    @dagger.Component.Factory
    interface Factory {

        fun create(
            dependencies: Dependencies,
            @BindsInstance transactionRepository: TransactionRepository = TransactionRepository.Noop
        ): TransactionComponent
    }

    @dagger.Module
    object Module {

        @Provides
        @TransactionScope
        fun viewProvider(
            transactionRepository: TransactionRepository
        ): ViewProvider = TransactionViewProvider(
            transactionRepository = transactionRepository
        )
    }
}

