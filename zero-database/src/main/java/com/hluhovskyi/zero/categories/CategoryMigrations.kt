package com.hluhovskyi.zero.categories

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE CategoryEntity ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
    }
}
