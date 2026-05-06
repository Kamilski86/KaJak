package de.ca.qcc.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MismatchEntity::class, ScanEntity::class],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mismatchDao(): MismatchDao
    abstract fun scanDao(): ScanDao
}
