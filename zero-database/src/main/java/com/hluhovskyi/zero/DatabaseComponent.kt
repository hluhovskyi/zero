package com.hluhovskyi.zero

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.hluhovskyi.zero.accounts.AccountRepository
import com.hluhovskyi.zero.accounts.MIGRATION_1_2
import com.hluhovskyi.zero.accounts.MIGRATION_3_4
import com.hluhovskyi.zero.accounts.MIGRATION_5_6
import com.hluhovskyi.zero.accounts.RoomAccountRepository
import com.hluhovskyi.zero.accounts.RoomAccountSyncSink
import com.hluhovskyi.zero.accounts.RoomAccountSyncSource
import com.hluhovskyi.zero.budget.BudgetRepository
import com.hluhovskyi.zero.budget.MIGRATION_7_8
import com.hluhovskyi.zero.budget.RoomBudgetRepository
import com.hluhovskyi.zero.budget.RoomBudgetSyncSink
import com.hluhovskyi.zero.budget.RoomBudgetSyncSource
import com.hluhovskyi.zero.categories.CategoryRepository
import com.hluhovskyi.zero.categories.MIGRATION_6_7
import com.hluhovskyi.zero.categories.RoomCategoryRepository
import com.hluhovskyi.zero.categories.RoomCategorySyncSink
import com.hluhovskyi.zero.categories.RoomCategorySyncSource
import com.hluhovskyi.zero.common.Id
import com.hluhovskyi.zero.common.IdGenerator
import com.hluhovskyi.zero.common.IncorrectStateDetector
import com.hluhovskyi.zero.common.Logger
import com.hluhovskyi.zero.common.time.ZonedClock
import com.hluhovskyi.zero.config.ConfigurationRepository
import com.hluhovskyi.zero.config.RoomConfigurationRepository
import com.hluhovskyi.zero.currencies.CurrencyRepository
import com.hluhovskyi.zero.currencies.InUseCurrencyRepository
import com.hluhovskyi.zero.sync.EntitySyncSink
import com.hluhovskyi.zero.sync.EntitySyncSource
import com.hluhovskyi.zero.sync.SyncAccount
import com.hluhovskyi.zero.sync.SyncBudget
import com.hluhovskyi.zero.sync.SyncCategory
import com.hluhovskyi.zero.sync.SyncTransaction
import com.hluhovskyi.zero.transactions.MIGRATION_4_5
import com.hluhovskyi.zero.transactions.RoomTransactionRepository
import com.hluhovskyi.zero.transactions.RoomTransactionSyncSink
import com.hluhovskyi.zero.transactions.RoomTransactionSyncSource
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
    dependencies = [DatabaseComponent.Dependencies::class],
)
interface DatabaseComponent {

    val currentUserRepository: CurrentUserRepository
    val accountRepository: AccountRepository
    val transactionRepository: TransactionRepository
    val categoryRepository: CategoryRepository
    val budgetRepository: BudgetRepository
    val configurationRepository: ConfigurationRepository

    val cleanupJob: CleanupJob

    val currencyRepositoryTransformer: CurrencyRepository.Transformer

    fun transform(repository: CurrencyRepository): CurrencyRepository = currencyRepositoryTransformer.transform(repository)

    // Sync sources/sinks — interface types only, no Room* type names in signatures
    fun categorySyncSource(): EntitySyncSource<SyncCategory>
    fun categorySyncSink(): EntitySyncSink<SyncCategory>
    fun accountSyncSource(): EntitySyncSource<SyncAccount>
    fun accountSyncSink(): EntitySyncSink<SyncAccount>
    fun transactionSyncSource(): EntitySyncSource<SyncTransaction>
    fun transactionSyncSink(): EntitySyncSink<SyncTransaction>
    fun budgetSyncSource(): EntitySyncSource<SyncBudget>
    fun budgetSyncSink(): EntitySyncSink<SyncBudget>

    interface Dependencies {

        val context: Context
        val zonedClock: ZonedClock
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
            context: Context,
        ): MainDatabase = Room.databaseBuilder(
            context,
            MainDatabase::class.java,
            "MainDatabase",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()

        @Provides
        @DatabaseScope
        internal fun cleanupJob(db: MainDatabase): CleanupJob = object : CleanupJob {
            override suspend fun clearAllTables() = db.clearAllTables()
            override suspend fun clearExceptUser() = db.withTransaction {
                val d = db.openHelper.writableDatabase
                d.execSQL("DELETE FROM AccountEntity")
                d.execSQL("DELETE FROM TransactionEntity")
                d.execSQL("DELETE FROM CategoryEntity")
                d.execSQL("DELETE FROM ConfigurationEntity")
                d.execSQL("DELETE FROM BudgetEntity")
            }
        }

        @Provides
        @DatabaseScope
        internal fun transactionRepository(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            incorrectStateDetector: IncorrectStateDetector,
            zonedClock: ZonedClock,
        ): TransactionRepository = RoomTransactionRepository(
            transactionRoom = { database.get().transaction() },
            currentUserId = currentUserId,
            incorrectStateDetector = incorrectStateDetector,
            zonedClock = zonedClock,
        )

        @Provides
        @DatabaseScope
        internal fun currentUserRepository(
            idGenerator: IdGenerator,
            logger: Logger,
            database: Provider<MainDatabase>,
        ): CurrentUserRepository = RoomCurrentUserRepository(
            idGenerator = idGenerator,
            logger = logger,
            currentUserRoom = { database.get().currentUser() },
        )

        @Provides
        @DatabaseScope
        internal fun accountRepository(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            incorrectStateDetector: IncorrectStateDetector,
            idGenerator: IdGenerator,
            zonedClock: ZonedClock,
        ): AccountRepository = RoomAccountRepository(
            accountRoom = { database.get().account() },
            currentUserId = currentUserId,
            incorrectStateDetector = incorrectStateDetector,
            idGenerator = idGenerator,
            zonedClock = zonedClock,
        )

        @Provides
        @DatabaseScope
        internal fun categoryRepository(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            idGenerator: IdGenerator,
            incorrectStateDetector: IncorrectStateDetector,
            zonedClock: ZonedClock,
        ): CategoryRepository = RoomCategoryRepository(
            categoryRoom = { database.get().category() },
            currentUserId = currentUserId,
            idGenerator = idGenerator,
            zonedClock = zonedClock,
            incorrectStateDetector = incorrectStateDetector,
        )

        @Provides
        @DatabaseScope
        internal fun budgetRepository(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            idGenerator: IdGenerator,
            incorrectStateDetector: IncorrectStateDetector,
            zonedClock: ZonedClock,
        ): BudgetRepository = RoomBudgetRepository(
            budgetRoom = { database.get().budget() },
            currentUserId = currentUserId,
            idGenerator = idGenerator,
            zonedClock = zonedClock,
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

        @Provides
        @DatabaseScope
        internal fun currencyRepositoryTransformer(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
            zonedClock: ZonedClock,
        ): CurrencyRepository.Transformer = CurrencyRepository.Transformer { baseRepository ->
            InUseCurrencyRepository(
                accountRoom = { database.get().account() },
                transactionRoom = { database.get().transaction() },
                baseRepository = baseRepository,
                currentUserId = currentUserId,
                zonedClock = zonedClock,
            )
        }

        @Provides
        @DatabaseScope
        internal fun categorySyncSource(
            database: Provider<MainDatabase>,
        ): EntitySyncSource<SyncCategory> = RoomCategorySyncSource(
            dao = { database.get().categorySync() },
        )

        @Provides
        @DatabaseScope
        internal fun categorySyncSink(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
        ): EntitySyncSink<SyncCategory> = RoomCategorySyncSink(
            dao = { database.get().categorySync() },
            currentUserId = currentUserId,
        )

        @Provides
        @DatabaseScope
        internal fun accountSyncSource(
            database: Provider<MainDatabase>,
        ): EntitySyncSource<SyncAccount> = RoomAccountSyncSource(
            dao = { database.get().accountSync() },
        )

        @Provides
        @DatabaseScope
        internal fun accountSyncSink(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
        ): EntitySyncSink<SyncAccount> = RoomAccountSyncSink(
            dao = { database.get().accountSync() },
            currentUserId = currentUserId,
        )

        @Provides
        @DatabaseScope
        internal fun transactionSyncSource(
            database: Provider<MainDatabase>,
        ): EntitySyncSource<SyncTransaction> = RoomTransactionSyncSource(
            dao = { database.get().transactionSync() },
        )

        @Provides
        @DatabaseScope
        internal fun transactionSyncSink(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
        ): EntitySyncSink<SyncTransaction> = RoomTransactionSyncSink(
            dao = { database.get().transactionSync() },
            currentUserId = currentUserId,
        )

        @Provides
        @DatabaseScope
        internal fun budgetSyncSource(
            database: Provider<MainDatabase>,
        ): EntitySyncSource<SyncBudget> = RoomBudgetSyncSource(
            dao = { database.get().budgetSync() },
        )

        @Provides
        @DatabaseScope
        internal fun budgetSyncSink(
            database: Provider<MainDatabase>,
            @CurrentUserId currentUserId: Flow<Id.Known>,
        ): EntitySyncSink<SyncBudget> = RoomBudgetSyncSink(
            dao = { database.get().budgetSync() },
            currentUserId = currentUserId,
        )
    }
}
