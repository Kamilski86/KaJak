package de.ca.rfidchecker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mismatches")
data class MismatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val epc: String,
    val sgtin: String,
    val gtinTag: String,
    val gtinQr: String
)

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val gtinQr: String,
    val gtinTag: String,
    val isMatch: Boolean,
    val sgtinQr: String = "",
    val sgtinTag: String = "",
    val qrRaw: String = ""
)
