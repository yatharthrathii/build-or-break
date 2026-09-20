package com.buildorbreak.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change, written by hand, kept forever.
 *
 * There is no destructive fallback on this database, so a version bump without
 * a migration here is a crash on launch for everyone who updates. That is the
 * intended failure: a crash gets fixed by shipping the migration, a wipe of
 * somebody's history cannot be undone.
 */
object Migrations {

    /** Adds `catchable` to items. Everything existing keeps the old behaviour, which was true. */
    val FROM_1_TO_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE item ADD COLUMN catchable INTEGER NOT NULL DEFAULT 1")
        }
    }

    /**
     * Adds the point ledger.
     *
     * A new table only, so there is nothing to convert and nobody upgrading
     * loses anything. An empty ledger means a balance equal to everything the
     * daily closes have earned, which is exactly right for somebody who has
     * never spent a point.
     */
    val FROM_2_TO_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `point_entry` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `at` INTEGER NOT NULL,
                    `date` INTEGER NOT NULL,
                    `delta` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_point_entry_date` ON `point_entry` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_point_entry_reason` ON `point_entry` (`reason`)")
        }
    }
}
