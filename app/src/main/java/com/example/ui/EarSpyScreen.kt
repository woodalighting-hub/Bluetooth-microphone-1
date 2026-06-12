package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Recording
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarSpyScreen(
    viewModel: EarSpyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLang by com.example.util.LanguageManager.currentLanguage.collectAsStateWithLifecycle()
    val s = com.example.util.LanguageManager.strings

    // Observe permission state
    val initialPermissions = remember {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        list
    }

    var permissionsGranted by remember {
        mutableStateOf(
            initialPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!permissionsGranted) {
                PermissionRequestScreen(
                    s = s,
                    permissions = initialPermissions,
                    onRequestPermissions = {
                        launcher.launch(initialPermissions.toTypedArray())
                    }
                )
            } else {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(
    s: com.example.util.AppStrings,
    permissions: List<String>,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color(0x1A00E676), CircleShape)
                .border(2.dp, NeonGreen, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permissions Needed",
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = s.permissionsTitle,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = s.permissionsDesc,
            fontSize = 14.sp,
            color = WhiteAlpha60,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonGreen,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("request_permissions_button")
        ) {
            Text(
                text = s.grantAccess,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun DashboardScreen(viewModel: EarSpyViewModel) {
    val currentLang by com.example.util.LanguageManager.currentLanguage.collectAsStateWithLifecycle()
    val s = com.example.util.LanguageManager.strings

    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val bluetoothStatus by viewModel.bluetoothScoStatus.collectAsStateWithLifecycle()
    val gainFactor by viewModel.gainFactor.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()
    val recordingSeconds by viewModel.recordingSeconds.collectAsStateWithLifecycle()
    val recordings by viewModel.recordingsList.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMessage.collectAsStateWithLifecycle()

    var activeDialogRecording by remember { mutableStateOf<Recording?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    var showLanguageSettings by remember { mutableStateOf(false) }

    // Rolling waveform history (40 bars)
    val waveHistory = remember { mutableStateListOf<Float>().apply { repeat(40) { add(0.05f) } } }
    LaunchedEffect(amplitude) {
        waveHistory.removeAt(0)
        // Add current peak amplitude with slight visual decay
        waveHistory.add(maxOf(0.06f, amplitude))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Header Status Hud
        HeaderHud(
            bluetoothStatus = bluetoothStatus,
            s = s,
            onSettingsClick = { showLanguageSettings = true }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Large Real-time Waveform Canvas
        WaveformDisplay(waveHistory = waveHistory)

        Spacer(modifier = Modifier.height(16.dp))

        // Main Intercept Panel (Tap buttons)
        InterceptControlPanel(
            s = s,
            isListening = isListening,
            isRecording = isRecording,
            recordingSeconds = recordingSeconds,
            onToggleListening = { viewModel.toggleListening() },
            onToggleRecording = { viewModel.toggleRecording() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Gain slide controller
        GainControlSlider(
            s = s,
            gainFactor = gainFactor,
            onGainChange = { value -> viewModel.setGain(value) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Interceptions list
        RecordingsLogHeader(s = s, count = recordings.size)

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (recordings.isEmpty()) {
                EmptyStateLogger(s = s)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recordings, key = { it.id }) { rec ->
                        val isPlaying = rec.id == viewModel.playingRecordingId.collectAsStateWithLifecycle().value
                        val progressSec = viewModel.playbackSeconds.collectAsStateWithLifecycle().value
                        val totalSec = viewModel.playbackDuration.collectAsStateWithLifecycle().value

                        RecordingItem(
                            s = s,
                            recording = rec,
                            isPlaying = isPlaying,
                            playProgressSeconds = progressSec,
                            totalSeconds = totalSec,
                            onPlayToggle = {
                                if (isPlaying) {
                                    viewModel.stopPlayback()
                                } else {
                                    viewModel.playRecording(rec)
                                }
                            },
                            onRename = {
                                activeDialogRecording = rec
                                renameInputText = rec.displayName
                                showRenameDialog = true
                            },
                            onDelete = {
                                viewModel.deleteRecording(rec)
                            }
                        )
                    }
                }
            }
        }
    }

    // Alarm/Error Alert snack banner
    if (errorMsg != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = CyberPink)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        s.systemWarning,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            text = { Text(errorMsg ?: "", color = WhiteAlpha90) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK", color = NeonGreen, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Rename Dialog
    if (showRenameDialog && activeDialogRecording != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text(
                    s.renameInterception,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column {
                    Text(s.enterCustomName, color = WhiteAlpha60, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = WhiteAlpha30,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rec = activeDialogRecording
                        if (rec != null && renameInputText.isNotBlank()) {
                            viewModel.renameRecording(rec, renameInputText.trim())
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                ) {
                    Text(s.save, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(s.cancel, color = WhiteAlpha60, fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Language Settings Dialog
    if (showLanguageSettings) {
        AlertDialog(
            onDismissRequest = { showLanguageSettings = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NeonGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        s.settingsTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            text = {
                Column {
                    Text(
                        s.appLanguage,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        s.selectLanguageDesc,
                        color = WhiteAlpha60,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // English selection option
                    LanguageOptionRow(
                        label = s.languageEn,
                        isSelected = currentLang == "en",
                        onClick = {
                            com.example.util.LanguageManager.setLanguage("en")
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Ukrainian selection option
                    LanguageOptionRow(
                        label = s.languageUk,
                        isSelected = currentLang == "uk",
                        onClick = {
                            com.example.util.LanguageManager.setLanguage("uk")
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageSettings = false }) {
                    Text(s.close, color = NeonGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}

@Composable
fun LanguageOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0x1F00E676) else DarkSurface)
            .border(
                1.dp,
                if (isSelected) NeonGreen else WhiteAlpha30,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) NeonGreen else Color.White,
            fontFamily = FontFamily.Monospace
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .border(2.dp, if (isSelected) NeonGreen else WhiteAlpha30, CircleShape)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NeonGreen, CircleShape)
                )
            }
        }
    }
}

@Composable
fun HeaderHud(
    bluetoothStatus: String,
    s: com.example.util.AppStrings,
    onSettingsClick: () -> Unit
) {
    val bColor = when (bluetoothStatus) {
        "Connected" -> NeonGreen
        "Connecting" -> ElectricBlue
        "Disconnected" -> CyberPink
        else -> WhiteAlpha30
    }

    val bText = when (bluetoothStatus) {
        "Connected" -> s.secureHeadsetActive
        "Connecting" -> s.bluetoothPairing
        "Disconnected" -> s.bluetoothDisconnected
        "Unsupported" -> s.scoChannelsUnavailable
        else -> bluetoothStatus.uppercase()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, WhiteAlpha30, RoundedCornerShape(8.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = s.appTagline,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = NeonGreen,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(bColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = bText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = bColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Radar pulse simulation circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(0x12FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.6f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "PulseScale"
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .drawBehind {
                            drawCircle(
                                color = bColor,
                                radius = size.minDimension / 2 * scale,
                                alpha = (1.2f - scale).coerceIn(0f, 1f)
                            )
                        }
                        .background(Color.Transparent, CircleShape)
                )
            }

            // Settings gear button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = NeonGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun WaveformDisplay(waveHistory: List<Float>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, WhiteAlpha30, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp)
    ) {
        // Scan line grids
        GridOverlay()

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            val width = size.width
            val height = size.height
            val barGap = 4.dp.toPx()
            val totalBars = waveHistory.size
            val barWidth = (width - (totalBars - 1) * barGap) / totalBars
            val centerY = height / 2

            // Draw symmetric waveform bars
            for (i in waveHistory.indices) {
                val amp = waveHistory[i]
                val rawHeight = amp * height * 0.85f
                val barHeight = maxOf(4.dp.toPx(), rawHeight) // Always keep a thin dot visual

                val x = i * (barWidth + barGap)
                val y = centerY - barHeight / 2

                drawRoundRect(
                    color = if (amp > 0.4f) CyberPink else NeonGreen,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2)
                )
            }
        }
    }
}

@Composable
fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val stepX = width / 6
        val stepY = height / 4

        // Draw helper vertical grid lines
        for (i in 1..5) {
            drawLine(
                color = Color(0x1100E676),
                start = Offset(stepX * i, 0f),
                end = Offset(stepX * i, height),
                strokeWidth = 1f
            )
        }

        // Draw helper horizontal grid lines
        for (i in 1..3) {
            drawLine(
                color = Color(0x1100E676),
                start = Offset(0f, stepY * i),
                end = Offset(width, stepY * i),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
fun InterceptControlPanel(
    s: com.example.util.AppStrings,
    isListening: Boolean,
    isRecording: Boolean,
    recordingSeconds: Int,
    onToggleListening: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle ear feedback (Listening)
        CardButton(
            title = s.interceptEarFeedback,
            isActive = isListening,
            activeColor = NeonGreen,
            inactiveColor = Color(0xFF1E2824),
            icon = if (isListening) Icons.Default.Close else Icons.Default.PlayArrow,
            label = if (isListening) s.stopMonitoring else s.startHearMode,
            onClick = onToggleListening,
            modifier = Modifier
                .weight(1f)
                .testTag("intercept_feedback_card")
        )

        // Toggle file Recording
        val minutes = recordingSeconds / 60
        val seconds = recordingSeconds % 60
        val timerLabel = String.format("%02d:%02d", minutes, seconds)

        CardButton(
            title = s.recordInterception,
            isActive = isRecording,
            activeColor = RedNeon,
            inactiveColor = Color(0xFF2B1417),
            icon = if (isRecording) Icons.Default.Close else Icons.Default.Star, // Pulse placeholder
            label = if (isRecording) s.recordingLabel.format(timerLabel) else s.startRecord,
            onClick = onToggleRecording,
            modifier = Modifier
                .weight(1f)
                .testTag("record_interception_card")
        )
    }
}

@Composable
fun CardButton(
    title: String,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerBg = if (isActive) activeColor else DarkSurface
    val labelColor = if (isActive) Color.Black else WhiteAlpha90
    val titleColor = if (isActive) Color.Black.copy(alpha = 0.6f) else WhiteAlpha30

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(
                width = 1.dp,
                color = if (isActive) Color.Transparent else WhiteAlpha30,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Standard custom icon structure
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (isActive) Color.Black.copy(alpha = 0.15f) else inactiveColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (icon == Icons.Default.Star) {
                // Return simple retro pulsing REC dot for a cool spy app
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(RedNeon, CircleShape)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) Color.Black else NeonGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GainControlSlider(
    s: com.example.util.AppStrings,
    gainFactor: Float,
    onGainChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .border(1.dp, WhiteAlpha30, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = s.amplifierGainBoost,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WhiteAlpha60,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format("x%.1f", gainFactor),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = NeonGreen,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = gainFactor,
            onValueChange = onGainChange,
            valueRange = 1.0f..8.0f,
            steps = 14, // Increments of 0.5
            colors = SliderDefaults.colors(
                thumbColor = NeonGreen,
                activeTrackColor = NeonGreen,
                inactiveTrackColor = Color(0x3300E676)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gain_amplifier_slider")
        )
    }
}

@Composable
fun RecordingsLogHeader(
    s: com.example.util.AppStrings,
    count: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = s.interceptionsLog,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WhiteAlpha60,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x1F00E676))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = s.totalSuffix.format(count),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = NeonGreen,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun RecordingItem(
    s: com.example.util.AppStrings,
    recording: Recording,
    isPlaying: Boolean,
    playProgressSeconds: Int,
    totalSeconds: Int,
    onPlayToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val simpleDate = remember(recording.timestamp) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(recording.timestamp))
    }

    val displayDuration = remember(recording.durationMs) {
        val totalSecs = (recording.durationMs / 1000).toInt()
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        String.format("%02d:%02d", mins, secs)
    }

    val displayFileSize = remember(recording.filePath) {
        val file = File(recording.filePath)
        if (file.exists()) {
            val bytes = file.length()
            if (bytes < 1024 * 1024) {
                String.format("%.1f KB", bytes / 1024f)
            } else {
                String.format("%.1f MB", bytes / (1024f * 1024f))
            }
        } else {
            "0.0 KB"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlaying) DarkSurfaceElevated else DarkSurface)
            .border(
                1.dp,
                if (isPlaying) NeonGreen else WhiteAlpha30,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini play button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isPlaying) NeonGreen else Color(0x1F00E676),
                        CircleShape
                    )
                    .clickable(onClick = onPlayToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = if (isPlaying) Color.Black else NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPlaying) NeonGreen else Color.White,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = simpleDate,
                        fontSize = 11.sp,
                        color = WhiteAlpha60,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = WhiteAlpha30,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = displayDuration,
                        fontSize = 11.sp,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = WhiteAlpha30,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = displayFileSize,
                        fontSize = 11.sp,
                        color = WhiteAlpha60,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Quick actions
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = WhiteAlpha60, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CyberPink, modifier = Modifier.size(16.dp))
            }
        }

        // Expanded play progress bar
        if (isPlaying) {
            val progressPercent = if (totalSeconds > 0) playProgressSeconds.toFloat() / totalSeconds else 0f
            val currentSecondsLabel = String.format("%02d:%02d", playProgressSeconds / 60, playProgressSeconds % 60)
            val totalSecondsLabel = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)

            Spacer(modifier = Modifier.height(12.dp))

            Column {
                LinearProgressIndicator(
                    progress = { progressPercent },
                    color = NeonGreen,
                    trackColor = Color(0x2200E676),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.playingBack,
                        fontSize = 10.sp,
                        color = NeonGreen,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "$currentSecondsLabel / $totalSecondsLabel",
                        fontSize = 10.sp,
                        color = WhiteAlpha90,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateLogger(s: com.example.util.AppStrings) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(1.dp, WhiteAlpha30, RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Empty Log",
            tint = WhiteAlpha30,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = s.noInterceptionsSaved,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WhiteAlpha60,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = s.emptyLogTip,
            fontSize = 11.sp,
            color = WhiteAlpha30,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
