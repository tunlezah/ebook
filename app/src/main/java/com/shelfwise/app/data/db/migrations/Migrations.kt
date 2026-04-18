package com.shelfwise.app.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `books_fts` " +
                    "USING FTS4(`title` TEXT NOT NULL, `author` TEXT NOT NULL, " +
                    "content=`books`)"
        )
        // Populate from the existing books table so search works on
        // existing libraries without requiring a rescan.
        db.execSQL(
            "INSERT INTO `books_fts`(`rowid`, `title`, `author`) " +
                    "SELECT `id`, `title`, `author` FROM `books`"
        )
        // FTS4 with `content=books` is an "external-content" table: SQLite
        // does NOT propagate changes from `books` to `books_fts` automatically,
        // and Room does not generate sync triggers for `contentEntity` either.
        // Install triggers so inserts/updates/deletes on `books` keep the FTS
        // index in lockstep. Without these, newly scanned books would be
        // unsearchable until a manual rebuild.
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `books_fts_ai` " +
                    "AFTER INSERT ON `books` BEGIN " +
                    "INSERT INTO `books_fts`(`rowid`, `title`, `author`) " +
                    "VALUES (new.`id`, new.`title`, new.`author`); END;"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `books_fts_ad` " +
                    "AFTER DELETE ON `books` BEGIN " +
                    "DELETE FROM `books_fts` WHERE `rowid` = old.`id`; END;"
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS `books_fts_au` " +
                    "AFTER UPDATE OF `title`, `author` ON `books` BEGIN " +
                    "UPDATE `books_fts` SET `title` = new.`title`, " +
                    "`author` = new.`author` WHERE `rowid` = new.`id`; END;"
        )
    }
}

object AllMigrations {
    val LIST: Array<Migration> = arrayOf(MIGRATION_1_2)
}
