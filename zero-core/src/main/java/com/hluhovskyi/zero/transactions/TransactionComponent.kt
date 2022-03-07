package com.hluhovskyi.zero.transactions

import com.hluhovskyi.zero.common.AttachableViewComponent
import com.hluhovskyi.zero.common.Buildable
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

        fun builder(dependencies: Dependencies): Builder = DaggerTransactionComponent.builder()
            .dependencies(dependencies)
            .transactionRepository(TransactionRepository.Noop)
    }

    @dagger.Component.Builder
    interface Builder : Buildable<TransactionComponent> {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun transactionRepository(transactionRepository: TransactionRepository): Builder
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

