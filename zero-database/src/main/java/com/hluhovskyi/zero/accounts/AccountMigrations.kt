package com.hluhovskyi.zero.accounts

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE AccountEntity ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'")
        db.execSQL("ALTER TABLE AccountEntity ADD COLUMN details TEXT")
    }
}

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE AccountEntity ADD COLUMN colorId TEXT")
    }
}
