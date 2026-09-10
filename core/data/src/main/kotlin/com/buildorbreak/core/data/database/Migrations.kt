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
}
