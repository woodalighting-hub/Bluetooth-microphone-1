package com.example.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val recordingDao: RecordingDao) {

    val allRecordings: Flow<List<Recording>> = recordingDao.getAllRecordings()

    suspend fun insert(recording: Recording): Long {
        return recordingDao.insertRecording(recording)
    }

    suspend fun update(recording: Recording) {
        recordingDao.updateRecording(recording)
    }

    suspend fun delete(recording: Recording) {
        recordingDao.deleteRecording(recording)
    }

    suspend fun getRecordingById(id: Int): Recording? {
        return recordingDao.getRecordingById(id)
    }
}
