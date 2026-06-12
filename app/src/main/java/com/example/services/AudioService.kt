package com.example.services

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.controller.EarSpyController
import com.example.data.AppDatabase
import com.example.data.Recording
import com.example.data.RecordingRepository
import com.example.util.LanguageManager
import kotlinx.coroutines.*
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioLoopJob: Job? = null

    private lateinit var audioManager: AudioManager
    private lateinit var repository: RecordingRepository

    private var isScoReceiverRegistered = false

    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private val CHANNEL_ID = "EarSpyChannel"
    private val NOTIFICATION_ID = 1001

    // State variables for the active recording
    private var activeRecordingFile: File? = null
    private var activeRecordingStartTime: Long = 0L
    private var totalBytesWritten: Long = 0L

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
            when (state) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                    audioManager.isBluetoothScoOn = true
                    EarSpyController.setBluetoothScoStatus("Connected")
                }
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> {
                    EarSpyController.setBluetoothScoStatus("Connecting")
                }
                AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                    audioManager.isBluetoothScoOn = false
                    EarSpyController.setBluetoothScoStatus("Disconnected")
                    // If SCO disconnected, we attempt to reconnect/retry SCO if we are still active
                    if (EarSpyController.isListening.value || EarSpyController.isRecording.value) {
                        try {
                            audioManager.startBluetoothSco()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                AudioManager.SCO_AUDIO_STATE_ERROR -> {
                    EarSpyController.setBluetoothScoStatus("Disconnected")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LanguageManager.init(applicationContext)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val db = AppDatabase.getDatabase(applicationContext)
        repository = RecordingRepository(db.recordingDao())

        // Create notification channel
        createNotificationChannel()

        // Register Bluetooth SCO receiver
        val filter = IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        registerReceiver(scoReceiver, filter)
        isScoReceiverRegistered = true

        // Route to SCO if possible
        try {
            if (audioManager.isBluetoothScoAvailableOffCall) {
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                EarSpyController.setBluetoothScoStatus("Connecting")
            } else {
                EarSpyController.setBluetoothScoStatus("Unsupported")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            EarSpyController.setBluetoothScoStatus("Disconnected")
        }

        EarSpyController.setServiceRunning(true)
        startAudioLoop()
    }

    private fun startAudioLoop() {
        audioLoopJob?.cancel()
        audioLoopJob = serviceScope.launch {
            val minBufIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
            val bufferSizeInBytes = maxOf(minBufIn, 2048)
            val bufferSizeInShorts = bufferSizeInBytes / 2

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, // MIC triggers SCO capture automatically when isBluetoothScoOn = true
                    SAMPLE_RATE,
                    CHANNEL_CONFIG_IN,
                    AUDIO_FORMAT,
                    bufferSizeInBytes
                ).apply {
                    if (state != AudioRecord.STATE_INITIALIZED) {
                        throw IllegalStateException("AudioRecord initialization failed!")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    EarSpyController.setErrorMessage(LanguageManager.strings.micFailed.format(e.localizedMessage))
                }
                return@launch
            }
            audioRecord = record

            val minBufOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT)
            val bufferSizeOutBytes = maxOf(minBufOut, 2048)
            val track = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioTrack(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_OUT)
                            .setEncoding(AUDIO_FORMAT)
                            .build(),
                        bufferSizeOutBytes,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG_OUT,
                        AUDIO_FORMAT,
                        bufferSizeOutBytes,
                        AudioTrack.MODE_STREAM
                    )
                }.apply {
                    if (state != AudioTrack.STATE_INITIALIZED) {
                        throw IllegalStateException("AudioTrack initialization failed!")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    EarSpyController.setErrorMessage(LanguageManager.strings.micFailed.format(e.localizedMessage))
                }
                record.release()
                return@launch
            }
            audioTrack = track

            // Start recording input
            try {
                record.startRecording()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    EarSpyController.setErrorMessage(LanguageManager.strings.micUnavail.format(e.localizedMessage))
                }
                record.release()
                track.release()
                return@launch
            }

            // Always track changes to listening & play/pause accordingly
            var isTrackPlaying = false

            val shortBuffer = ShortArray(512) // Read in smaller chunks for lowest latency
            val byteBuffer = ByteArray(1024)

            var recorderStream: BufferedOutputStream? = null

            try {
                while (isActive) {
                    // 1. Read PCM input
                    val readResult = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readResult <= 0) {
                        delay(10)
                        continue
                    }

                    // 2. Calculate RMS amplitude for real-time visualization of mic level
                    var maxVal = 0
                    for (i in 0 until readResult) {
                        val absVal = Math.abs(shortBuffer[i].toInt())
                        if (absVal > maxVal) {
                            maxVal = absVal
                        }
                    }
                    val currentAmplitude = maxVal.toFloat() / Short.MAX_VALUE
                    EarSpyController.updateAmplitude(currentAmplitude)

                    // 3. Process sound (Amplification)
                    val gain = EarSpyController.gainFactor.value
                    val processedBuffer = if (gain != 1.0f) {
                        ShortArray(readResult) { index ->
                            val valAmp = (shortBuffer[index] * gain).toInt()
                            valAmp.coerceIn(-32768, 32767).toShort()
                        }
                    } else {
                        shortBuffer
                    }

                    // 4. Playback if real-time hearing is enabled
                    val shouldPlay = EarSpyController.isListening.value
                    if (shouldPlay) {
                        if (!isTrackPlaying) {
                            try {
                                track.play()
                                isTrackPlaying = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        track.write(processedBuffer, 0, readResult)
                    } else {
                        if (isTrackPlaying) {
                            try {
                                track.pause()
                                track.flush()
                                isTrackPlaying = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // 5. Save to file if recording is active
                    val shouldRecord = EarSpyController.isRecording.value
                    if (shouldRecord) {
                        if (recorderStream == null) {
                            // Start new file stream
                            val docsDir = File(filesDir, "recordings")
                            if (!docsDir.exists()) {
                                docsDir.mkdirs()
                            }
                            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            val defaultName = "Recording_${sdf.format(Date())}"
                            val file = File(docsDir, "$defaultName.wav")
                            activeRecordingFile = file
                            activeRecordingStartTime = System.currentTimeMillis()
                            totalBytesWritten = 0L

                            recorderStream = BufferedOutputStream(FileOutputStream(file))
                            // Write WAV dummy header placeholder (44 bytes) to be rewritten upon completion
                            recorderStream.write(ByteArray(44))
                            
                            // Start recording duration timer scope
                            launch {
                                while (EarSpyController.isRecording.value) {
                                    val elapsed = ((System.currentTimeMillis() - activeRecordingStartTime) / 1000).toInt()
                                    EarSpyController.updateRecordingSeconds(elapsed)
                                    delay(500)
                                }
                            }
                        }

                        // Convert shorts (processed / amplified) to little-endian bytes for the WAV file
                        for (i in 0 until readResult) {
                            val sample = processedBuffer[i]
                            byteBuffer[2 * i] = (sample.toInt() and 0xff).toByte()
                            byteBuffer[2 * i + 1] = ((sample.toInt() shr 8) and 0xff).toByte()
                        }

                        val bytesToWrite = readResult * 2
                        recorderStream.write(byteBuffer, 0, bytesToWrite)
                        totalBytesWritten += bytesToWrite
                    } else {
                        if (recorderStream != null) {
                            // Finish and close file stream
                            try {
                                recorderStream.flush()
                                recorderStream.close()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            recorderStream = null

                            // Fix WAV header with final size parameters
                            val file = activeRecordingFile
                            if (file != null && file.exists() && totalBytesWritten > 0) {
                                fixWavHeader(file, totalBytesWritten)
                                // Add details to the database
                                val duration = (System.currentTimeMillis() - activeRecordingStartTime)
                                val simpleName = file.nameWithoutExtension
                                val recording = Recording(
                                    filePath = file.absolutePath,
                                    fileName = file.name,
                                    displayName = simpleName,
                                    timestamp = System.currentTimeMillis(),
                                    durationMs = duration
                                )
                                repository.insert(recording)
                            }
                            activeRecordingFile = null
                            EarSpyController.updateRecordingSeconds(0)
                        }
                    }
                }
            } finally {
                // Final cleanups of capture/playback threads
                try {
                    record.stop()
                    record.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    recorderStream?.flush()
                    recorderStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val file = activeRecordingFile
                if (file != null && file.exists() && totalBytesWritten > 0) {
                    fixWavHeader(file, totalBytesWritten)
                    val duration = (System.currentTimeMillis() - activeRecordingStartTime)
                    val simpleName = file.nameWithoutExtension
                    val recording = Recording(
                        filePath = file.absolutePath,
                        fileName = file.name,
                        displayName = simpleName,
                        timestamp = System.currentTimeMillis(),
                        durationMs = duration
                    )
                    repository.insert(recording)
                }
                EarSpyController.updateRecordingSeconds(0)
            }
        }
    }

    private fun fixWavHeader(file: File, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = SAMPLE_RATE * channels * 2L // 16-bit is 2 bytes

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Header length (16 bytes)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Format = PCM
        header[21] = 0
        header[22] = channels.toByte() // Mono
        header[23] = 0
        header[24] = (SAMPLE_RATE and 0xff).toByte() // Sample rate
        header[25] = ((SAMPLE_RATE shr 8) and 0xff).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xff).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte() // Byte rate
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte() // Block align
        header[33] = 0
        header[34] = 16 // Bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte() // 'data' chunk header
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte() // Size of data chunk
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        try {
            val raf = RandomAccessFile(file, "rw")
            raf.seek(0)
            raf.write(header)
            raf.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Ear Spy Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the real-time background audio listening active."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val isRec = EarSpyController.isRecording.value
        val isList = EarSpyController.isListening.value
        val s = LanguageManager.strings
        val content = when {
            isRec && isList -> s.activeRecordingNotif
            isRec -> s.activeRecNotifOnly
            isList -> s.activeListNotifOnly
            else -> s.standbyNotif
        }

        val pendingIntent = Intent(this, getMainActivityClass()).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(s.activeNotifTitle)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun getMainActivityClass(): Class<*> {
        return try {
            Class.forName("com.example.MainActivity")
        } catch (e: Exception) {
            this.javaClass
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        audioLoopJob?.cancel()
        serviceJob.cancel()

        // Unregister SCO receiver
        if (isScoReceiverRegistered) {
            try {
                unregisterReceiver(scoReceiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isScoReceiverRegistered = false
        }

        // Clean up audio routing
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            e.printStackTrace()
        }

        EarSpyController.setServiceRunning(false)
        EarSpyController.updateAmplitude(0.0f)
    }
}
