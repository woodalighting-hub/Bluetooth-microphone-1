package com.example.controller

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.services.AudioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EarSpyController {

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _gainFactor = MutableStateFlow(2.0f) // Default amplification multiplier
    val gainFactor: StateFlow<Float> = _gainFactor.asStateFlow()

    private val _amplitude = MutableStateFlow(0.0f) // 0.0f to 1.0f representation of current sound
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _bluetoothScoStatus = MutableStateFlow("Disconnected") // Disconnected, Connecting, Connected, Unsupported
    val bluetoothScoStatus: StateFlow<String> = _bluetoothScoStatus.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun setListening(context: Context, enabled: Boolean) {
        _isListening.value = enabled
        updateServiceState(context)
    }

    fun setRecording(context: Context, enabled: Boolean) {
        _isRecording.value = enabled
        updateServiceState(context)
    }

    fun setGainFactor(gain: Float) {
        _gainFactor.value = gain
    }

    fun updateAmplitude(amp: Float) {
        _amplitude.value = amp
    }

    fun updateRecordingSeconds(seconds: Int) {
        _recordingSeconds.value = seconds
    }

    fun setBluetoothScoStatus(status: String) {
        _bluetoothScoStatus.value = status
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }

    private fun updateServiceState(context: Context) {
        val shouldRun = _isListening.value || _isRecording.value
        if (shouldRun) {
            val intent = Intent(context, AudioService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            val intent = Intent(context, AudioService::class.java)
            context.stopService(intent)
        }
    }
}
