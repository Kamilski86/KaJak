package de.ca.rfidchecker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE mismatches ADD COLUMN exportedAt INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Schema stabilization — no structural change
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS scans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                gtinQr TEXT NOT NULL,
                gtinTag TEXT NOT NULL,
                isMatch INTEGER NOT NULL
            )"""
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scans ADD COLUMN sgtinQr TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE scans ADD COLUMN sgtinTag TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE scans ADD COLUMN qrRaw TEXT NOT NULL DEFAULT ''")
    }
}

// Removes the unused exportedAt column from mismatches.
// SQLite on API < 35 has no DROP COLUMN, so we recreate the table.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS mismatches_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                epc TEXT NOT NULL,
                sgtin TEXT NOT NULL,
                gtinTag TEXT NOT NULL,
                gtinQr TEXT NOT NULL
            )"""
        )
        db.execSQL(
            "INSERT INTO mismatches_new SELECT id, timestamp, epc, sgtin, gtinTag, gtinQr FROM mismatches"
        )
        db.execSQL("DROP TABLE mismatches")
        db.execSQL("ALTER TABLE mismatches_new RENAME TO mismatches")
    }
}