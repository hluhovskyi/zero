package com.hluhovskyi.zero.transactions

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE TransactionEntity ADD COLUMN notes TEXT")
    }
}

// Swap the userId index for a (userId, enteredDateTime) composite so the window query range-scans.
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS index_TransactionEntity_userId")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_TransactionEntity_userId_enteredDateTime " +
                "ON TransactionEntity (userId, enteredDateTime)",
        )
    }
}
