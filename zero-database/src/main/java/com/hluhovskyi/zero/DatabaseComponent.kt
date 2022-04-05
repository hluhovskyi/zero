package com.hluhovskyi.zero

import android.content.Context
import androidx.room.Room
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.RoomAccountRepository
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.RoomCategoryRepository
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.time.Clock
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.RoomConfigurationRepository
import com.hluhovskyi.zero.transactions.RoomTransactionRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import com.hluhovskyi.zero.users.CurrentUserRepository
import com.hluhovskyi.zero.users.RoomCurrentUserRepository
import dagger.BindsInstance
import dagger.Provides
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class DatabaseScope

@Qualifier
@Retention(AnnotationRetention.SOURCE)
private annotation class CurrentUserId

@DatabaseScope
@dagger.Component(
    modules = [DatabaseComponent.Module::class],
    dependencies = [DatabaseComponent.Dependencies::class]
)
interface DatabaseComponent {

    val currentUserRepository: CurrentUserRepository
    val accountRepository: AccountRepository
    val transactionRepository: TransactionRepository
    val categoryRepository: CategoryRepository
    val configurationRepository: ConfigurationRepository

    interface Dependencies {

        val context: Context
        val clock: Clock
    }

    companion object {

        fun builder(dependencies: Dependencies): Builder = DaggerDatabaseComponent.builder()
            .dependencies(dependencies)
            .logger(Logger.Noop)
            .idGenerator(IdGenerator.UUID)
            .incorrectStateDetector(IncorrectStateDetector.ignoreIncorrect())
            .currentUserId(emptyFlow())
    }

    @dagger.Component.Builder
    interface Builder {

        fun dependencies(dependencies: Dependencies): Builder

        @BindsInstance
        fun logger(logger: Logger): Builder

        @BindsInstance
        fun idGenerator(idGenerator: IdGenerator): Builder

        @BindsInstance
        fun incorrectStateDetector(incorrectStateDetector: IncorrectStateDetector): Builder

        @BindsInstance
        fun currentUserId(@CurrentUserId currentUserId: Flow<Id.Known>): Builder

        fun build(): DatabaseComponent
    }

    @dagger.Module
    object Module {

        @Provides
        @DatabaseScope
        internal fun mainDatabase(
            context: Context
        ): MainDatabase = Room.databaseBuilder(
            context,
            MainDatabase::class.java,
            "MainDatabase"
        ).build()

        @Provides
        @DatabaseScope
        internal fun transactionRepository(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            incorrectStateDetector: IncorrectStateDetector,
            clock: Clock,
        ): TransactionRepository = RoomTransactionRepository(
            transactionRoom = { database.get().transaction() },
            currentUserId = currentUserId,
            incorrectStateDetector = incorrectStateDetector,
            clock = clock,
        )

        @Provides
        @DatabaseScope
        internal fun currentUserRepository(
            idGenerator: IdGenerator,
            logger: Logger,
            database: Provider<MainDatabase>
        ): CurrentUserRepository = RoomCurrentUserRepository(
            idGenerator = idGenerator,
            logger = logger,
            currentUserRoom = { database.get().currentUser() }
        )

        @Provides
        @DatabaseScope
        internal fun accountRepository(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            incorrectStateDetector: IncorrectStateDetector,
            idGenerator: IdGenerator,
        ): AccountRepository = RoomAccountRepository(
            accountRoom = { database.get().account() },
            currentUserId = currentUserId,
            incorrectStateDetector = incorrectStateDetector,
            idGenerator = idGenerator
        )

        @Provides
        @DatabaseScope
        internal fun categoryRepository(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            idGenerator: IdGenerator,
            incorrectStateDetector: IncorrectStateDetector,
            clock: Clock,
        ): CategoryRepository = RoomCategoryRepository(
            categoryRoom = { database.get().category() },
            currentUserId = currentUserId,
            idGenerator = idGenerator,
            clock = clock,
            incorrectStateDetector = incorrectStateDetector,
        )

        @Provides
        @DatabaseScope
        internal fun configurationRepository(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            incorrectStateDetector: IncorrectStateDetector,
            logger: Logger,
        ): ConfigurationRepository = RoomConfigurationRepository(
            configurationRoom = { database.get().configuration() },
            currentUserId = currentUserId,
            incorrectStateDetector = incorrectStateDetector,
            logger = logger,
        )
    }
}