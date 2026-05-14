package com.hluhovskyi.zero.budget

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `BudgetEntity` (" +
                "`id` TEXT NOT NULL, " +
                "`userId` TEXT NOT NULL, " +
                "`categoryId` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`amount` REAL NOT NULL, " +
                "`periodStart` TEXT NOT NULL, " +
                "`periodEnd` TEXT NOT NULL, " +
                "`creationDateTime` TEXT NOT NULL, " +
                "`updatedDateTime` TEXT NOT NULL, " +
                "`deletedAt` TEXT, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_BudgetEntity_userId` ON `BudgetEntity` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_BudgetEntity_categoryId` ON `BudgetEntity` (`categoryId`)")
    }
}
