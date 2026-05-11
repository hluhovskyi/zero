package com.hluhovskyi.zero.categories

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE CategoryEntity ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
    }
}
