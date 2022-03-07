package com.hluhovskyi.zero

import android.content.Context
import androidx.room.Room
import com.hluhovskyi.zero.transactions.RoomTransactionRepository
import com.hluhovskyi.zero.transactions.TransactionRepository
import dagger.Provides
import javax.inject.Provider
import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.SOURCE)
private annotation class DatabaseScope

@DatabaseScope
@dagger.Component(
    modules = [DatabaseComponent.Module::class],
    dependencies = [DatabaseComponent.Dependencies::class]
)
interface DatabaseComponent {

    val transactionRepository: TransactionRepository

    interface Dependencies {

        val context: Context
    }

    companion object {

        fun factory(): DatabaseComponent.Factory = DaggerDatabaseComponent.factory()
    }

    @dagger.Component.Factory
    interface Factory {

        fun create(
            dependencies: Dependencies
        ): DatabaseComponent
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
            database: Provider<MainDatabase>
        ): TransactionRepository = RoomTransactionRepository(
            transactionRoom = { database.get().transaction() }
        )
    }
}