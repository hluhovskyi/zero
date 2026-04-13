package com.hluhovskyi.zero

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // AccountEntity: add creationDateTime, updatedDateTime (with sentinel default), deletedAt
        db.execSQL(
            "ALTER TABLE AccountEntity ADD COLUMN creationDateTime TEXT NOT NULL DEFAULT '2000-01-01T00:00:00'",
        )
        db.execSQL(
            "ALTER TABLE AccountEntity ADD COLUMN updatedDateTime TEXT NOT NULL DEFAULT '2000-01-01T00:00:00'",
        )
        db.execSQL("ALTER TABLE AccountEntity ADD COLUMN deletedAt TEXT")

        // CategoryEntity: add deletedAt
        db.execSQL("ALTER TABLE CategoryEntity ADD COLUMN deletedAt TEXT")

        // TransactionEntity: add deletedAt
        db.execSQL("ALTER TABLE TransactionEntity ADD COLUMN deletedAt TEXT")
    }
}
