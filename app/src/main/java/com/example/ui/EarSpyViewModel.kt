package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.controller.EarSpyController
import com.example.data.AppDatabase
import com.example.data.Recording
import com.example.data.RecordingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EarSpyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecordingRepository
    val recordingsList: StateFlow<List<Recording>>

    // Media Player states
    private var mediaPlayer: MediaPlayer? = null
    private var playbackProgressJob: Job? = null

    private val _playingRecordingId = MutableStateFlow<Int?>(null)
    val playingRecordingId: StateFlow<Int?> = _playingRecordingId.asStateFlow()

    private val _playbackSeconds = MutableStateFlow(0)
    val playbackSeconds: StateFlow<Int> = _playbackSeconds.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0)
    val playbackDuration: StateFlow<Int> = _playbackDuration.asStateFlow()

    // Control States (directly connected to EarSpyController for reactivity)
    val isListening = EarSpyController.isListening
    val isRecording = EarSpyController.isRecording
    val bluetoothScoStatus = EarSpyController.bluetoothScoStatus
    val gainFactor = EarSpyController.gainFactor
    val amplitude = EarSpyController.amplitude
    val recordingSeconds = EarSpyController.recordingSeconds
    val errorMessage = EarSpyController.errorMessage

    init {
        val database = AppDatabase.getDatabase(application)
        repository = RecordingRepository(database.recordingDao())
        
        recordingsList = repository.allRecordings
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    // Toggle real-time audio routing (ear spy listening)
    fun toggleListening() {
        val nextState = !isListening.value
        EarSpyController.setListening(getApplication(), nextState)
    }

    // Toggle recording audio
    fun toggleRecording() {
        val nextState = !isRecording.value
        EarSpyController.setRecording(getApplication(), nextState)
    }

    fun setGain(gain: Float) {
        EarSpyController.setGainFactor(gain)
    }

    fun clearError() {
        EarSpyController.setErrorMessage(null)
    }

    // Playback control
    fun playRecording(recording: Recording) {
        // Stop any active player first
        stopPlayback()

        val file = File(recording.filePath)
        if (!file.exists()) {
            val s = com.example.util.LanguageManager.strings
            EarSpyController.setErrorMessage(s.fileNotFound)
            return
        }

        try {
            val player = MediaPlayer().apply {
                setDataSource(recording.filePath)
                prepare()
                start()
            }
            mediaPlayer = player
            _playingRecordingId.value = recording.id
            _playbackDuration.value = player.duration / 1000

            player.setOnCompletionListener {
                stopPlayback()
            }

            playbackProgressJob = viewModelScope.launch {
                while (player.isPlaying) {
                    _playbackSeconds.value = player.currentPosition / 1000
                    delay(250)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val s = com.example.util.LanguageManager.strings
            EarSpyController.setErrorMessage(s.playbackFailed.format(e.localizedMessage))
        }
    }

    fun stopPlayback() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null

        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                player.release()
            }
        }
        mediaPlayer = null
        _playingRecordingId.value = null
        _playbackSeconds.value = 0
        _playbackDuration.value = 0
    }

    // Delete a recording
    fun deleteRecording(recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            // Stop if playing
            if (_playingRecordingId.value == recording.id) {
                withContext(Dispatchers.Main) {
                    stopPlayback()
                }
            }

            // Remove file
            val file = File(recording.filePath)
            if (file.exists()) {
                file.delete()
            }

            // Remove database row
            repository.delete(recording)
        }
    }

    // Rename a recording
    fun renameRecording(recording: Recording, newDisplayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (newDisplayName.isBlank()) return@launch

            val file = File(recording.filePath)
            val parent = file.parentFile
            val extension = file.extension
            val newFile = File(parent, "$newDisplayName.$extension")

            var updatedFilePath = recording.filePath
            var updatedFileName = recording.fileName

            if (file.exists() && !newFile.exists()) {
                val renamed = file.renameTo(newFile)
                if (renamed) {
                    updatedFilePath = newFile.absolutePath
                    updatedFileName = newFile.name
                }
            }

            val updatedRecording = recording.copy(
                displayName = newDisplayName,
                filePath = updatedFilePath,
                fileName = updatedFileName
            )
            repository.update(updatedRecording)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }
}
