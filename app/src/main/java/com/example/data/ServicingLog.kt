package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servicing_logs")
data class ServicingLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceName: String,
    val protocol: String,
    val operation: String,
    val status: String,
    val timestamp: Long = System.currentTimeMillis(),
    val logText: String,
    val details: String = ""
)
