package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ServicingLogDao {
    @Query("SELECT * FROM servicing_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ServicingLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ServicingLog)

    @Query("DELETE FROM servicing_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM servicing_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)
}
