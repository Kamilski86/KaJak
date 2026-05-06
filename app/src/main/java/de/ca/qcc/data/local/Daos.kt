package de.ca.qcc.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MismatchDao {
    @Query("SELECT * FROM mismatches ORDER BY timestamp DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<MismatchEntity>>

    @Query("SELECT COUNT(*) FROM mismatches WHERE date(timestamp/1000, 'unixepoch', 'localtime') = date('now', 'localtime')")
    fun observeTodayCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MismatchEntity): Long
}

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: ScanEntity): Long

    @Query("SELECT COUNT(*) FROM scans WHERE date(timestamp/1000, 'unixepoch', 'localtime') = date('now', 'localtime')")
    fun observeTodayCount(): Flow<Int>

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    suspend fun getAllForExport(): List<ScanEntity>
}
