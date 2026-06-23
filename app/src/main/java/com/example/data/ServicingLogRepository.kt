package com.example.data

import kotlinx.coroutines.flow.Flow

class ServicingLogRepository(private val servicingLogDao: ServicingLogDao) {
    val allLogs: Flow<List<ServicingLog>> = servicingLogDao.getAllLogs()

    suspend fun insertLog(log: ServicingLog) {
        servicingLogDao.insertLog(log)
    }

    suspend fun clearLogs() {
        servicingLogDao.clearAllLogs()
    }

    suspend fun deleteLog(id: Int) {
        servicingLogDao.deleteLogById(id)
    }
}
