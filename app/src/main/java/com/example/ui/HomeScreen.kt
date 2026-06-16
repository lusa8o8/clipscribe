package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.auth.AuthStatus
import com.example.auth.FirebaseAuthStateHolder
import com.example.capture.AudioCaptureService
import com.example.capture.CaptureSessionConsentState
import com.example.capture.CaptureServiceState
import com.example.capture.CaptureServiceStateHolder
import com.example.capture.AudioCaptureState
import com.example.capture.AudioCaptureStateHolder
import com.example.capture.AudioLevelStateHolder
import com.example.capture.RollingBufferState
import com.example.capture.RollingBufferStateHolder
import com.example.capture.FrozenAudioBufferHolder
import com.example.capture.MediaProjectionConsentStateHolder
import com.example.overlay.FloatingBubbleService
import com.example.overlay.FloatingBubbleStateHolder
import com.example.permissions.MediaProjectionPermissionHelper
import com.example.transcription.AudioPreparationState
import com.example.transcription.AudioPreparationStateHolder
import com.example.transcription.AudioPreparationResult
import com.example.transcription.AudioPreparationController
import com.example.transcription.TranscriptionState
import com.example.transcription.TranscriptionEngineMode
import com.example.transcription.TranscriptionStateHolder
import com.example.transcription.TranscriptionResultHolder
import com.example.transcription.TranscriptionResult
import com.example.transcription.TranscriptionController
import com.example.transcription.ModelPathResolver
import com.example.capture.CaptureModeState
import com.example.capture.CaptureModeStateHolder
import com.example.capture.CaptureModeController
import com.example.ui.DeveloperModeStateHolder
import com.example.permissions.PermissionManager
import com.example.ui.result.TranscriptResultSheet
import com.example.ui.result.TranscriptResultStateHolder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Observe Capture Mode state
    val captureModeState by CaptureModeStateHolder.state.collectAsState()

    // Observe Firebase anonymous auth state
    val authState by FirebaseAuthStateHolder.state.collectAsState()

    // Observe Developer Mode state
    val isDevModeEnabled by DeveloperModeStateHolder.isDeveloperModeEnabled.collectAsState()

    // Setup completeness evaluation
    val permissionManager = remember { PermissionManager(context) }
    var setupComplete by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                setupComplete = permissionManager.checkAllPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        setupComplete = permissionManager.checkAllPermissions()
    }

    // Observe floating bubble active status dynamically from StateHolder
    val isBubbleActive by FloatingBubbleStateHolder.isRunningFlow.collectAsState()

    // Observe MediaProjection session consent status
    val consentState by MediaProjectionConsentStateHolder.consentState.collectAsState()

    // Observe Capture service state
    val serviceState by CaptureServiceStateHolder.serviceState.collectAsState()
    val isServiceActive = serviceState == CaptureServiceState.ACTIVE

    // Observe Audio Capture state
    val captureState by AudioCaptureStateHolder.captureState.collectAsState()

    // Observe Audio Level
    val rmsLevel by AudioLevelStateHolder.rmsLevel.collectAsState()

    // Observe Rolling Buffer States
    val bufferState by RollingBufferStateHolder.bufferState.collectAsState()
    val bufferDurationSeconds by RollingBufferStateHolder.durationSeconds.collectAsState()
    val lastFrozenSampleCount by RollingBufferStateHolder.lastFrozenSampleCount.collectAsState()

    // Observe Audio Preparation States
    val preparationState by AudioPreparationStateHolder.preparationState.collectAsState()
    val preparedDurationSeconds by AudioPreparationStateHolder.preparedDurationSeconds.collectAsState()
    val preparedSampleCount by AudioPreparationStateHolder.preparedSampleCount.collectAsState()

    // Observe Live Transcription States
    val transcriptionState by TranscriptionStateHolder.state.collectAsState()
    val engineMode by TranscriptionStateHolder.engineMode.collectAsState()
    val transcriptText by TranscriptionResultHolder.latestText.collectAsState()
    val transcriptionDurationMs by TranscriptionResultHolder.durationMillis.collectAsState()
    val freeTierUsage by TranscriptionResultHolder.freeTierUsage.collectAsState()

    // Observe Transcript Result Sheet State
    val transcriptResultState by TranscriptResultStateHolder.state.collectAsState()

    val mediaProjectionPermissionHelper = remember { MediaProjectionPermissionHelper(context) }

    // MediaProjection Result Launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            MediaProjectionConsentStateHolder.markApproved(result.resultCode, result.data!!)
            val isMvpStarting = CaptureModeStateHolder.currentState() == CaptureModeState.STARTING
            if (isMvpStarting) {
                val success = CaptureModeController.startAfterConsent(context, result.resultCode, result.data)
                if (success) {
                    coroutineScope.launch {
                        Toast.makeText(context, "Capture mode started.", Toast.LENGTH_SHORT).show()
                        snackbarHostState.showSnackbar("Capture mode started.")
                    }
                } else {
                    coroutineScope.launch {
                        Toast.makeText(context, "Failed to start capture mode.", Toast.LENGTH_SHORT).show()
                        snackbarHostState.showSnackbar("Failed to start capture mode.")
                    }
                }
            } else {
                coroutineScope.launch {
                    Toast.makeText(context, "Capture session approved. Audio capture will be added next.", Toast.LENGTH_SHORT).show()
                    snackbarHostState.showSnackbar("Capture session approved. Audio capture will be added next.")
                }
            }
        } else {
            MediaProjectionConsentStateHolder.markDenied()
            val isMvpStarting = CaptureModeStateHolder.currentState() == CaptureModeState.STARTING
            if (isMvpStarting) {
                CaptureModeStateHolder.markOff()
                coroutineScope.launch {
                    Toast.makeText(context, "Capture permission was not approved.", Toast.LENGTH_SHORT).show()
                    snackbarHostState.showSnackbar("Capture permission was not approved.")
                }
            } else {
                coroutineScope.launch {
                    Toast.makeText(context, "Capture session was not approved.", Toast.LENGTH_SHORT).show()
                    snackbarHostState.showSnackbar("Capture session was not approved.")
                }
            }
        }
    }

    // React to Audio capture status changes with user-facing messages
    LaunchedEffect(captureState) {
        when (captureState) {
            AudioCaptureState.AUDIO_DETECTED -> {
                Toast.makeText(context, "Supported playback audio detected.", Toast.LENGTH_SHORT).show()
                snackbarHostState.showSnackbar("Supported playback audio detected.")
            }
            AudioCaptureState.NO_AUDIO_DETECTED -> {
                Toast.makeText(context, "No supported playback audio detected yet. Play audio in another app and try again.", Toast.LENGTH_LONG).show()
                snackbarHostState.showSnackbar("No supported playback audio detected yet. Play audio in another app and try again.")
            }
            AudioCaptureState.BLOCKED_OR_UNSUPPORTED -> {
                Toast.makeText(context, "This source may not allow audio capture, or no audio is playing.", Toast.LENGTH_LONG).show()
                snackbarHostState.showSnackbar("This source may not allow audio capture, or no audio is playing.")
            }
            AudioCaptureState.ERROR -> {
                Toast.makeText(context, "Could not start audio capture proof.", Toast.LENGTH_LONG).show()
                snackbarHostState.showSnackbar("Could not start audio capture proof.")
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ClipScribe",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("app_brand_title")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Main Branding Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ClipScribe",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("home_main_title")
                )
                Text(
                    text = "Capture what you just heard.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("home_main_subtitle")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.testTag("privacy_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Privacy Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (authState.status == AuthStatus.SIGNED_IN_ANONYMOUSLY) {
                                "On-device • Beta account"
                            } else {
                                "On-device • Signing in"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (isDevModeEnabled) {
                // Status Card showing ClipScribe subsystems
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "System Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Line dividing details
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        // Diagnostical row 1: Setup
                        DiagnosticRow(
                            label = "Setup Status",
                            value = "Complete",
                            valueColor = MaterialTheme.colorScheme.primary,
                            isComplete = true
                        )

                        val (authValue, authColor, authComplete) = when (authState.status) {
                            AuthStatus.NOT_STARTED -> Triple("Not started", MaterialTheme.colorScheme.outline, false)
                            AuthStatus.SIGNING_IN -> Triple("Signing in", MaterialTheme.colorScheme.secondary, false)
                            AuthStatus.SIGNED_IN_ANONYMOUSLY -> {
                                val shortUid = authState.uid?.takeLast(6) ?: "ready"
                                Triple("Anonymous $shortUid", MaterialTheme.colorScheme.primary, true)
                            }
                            AuthStatus.ERROR -> Triple(
                                authState.errorMessage?.take(28) ?: "Error",
                                MaterialTheme.colorScheme.error,
                                false
                            )
                        }
                        DiagnosticRow(
                            label = "Auth",
                            value = authValue,
                            valueColor = authColor,
                            isComplete = authComplete
                        )

                        val tokenReady = !authState.idToken.isNullOrBlank()
                        DiagnosticRow(
                            label = "Auth token",
                            value = if (tokenReady) "Ready" else "Pending",
                            valueColor = if (tokenReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            isComplete = tokenReady
                        )

                        // Diagnostical row 2: Bubble
                        DiagnosticRow(
                            label = "Bubble",
                            value = if (isBubbleActive) "On" else "Off",
                            valueColor = if (isBubbleActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            isComplete = isBubbleActive
                        )

                        // Diagnostical row 3: Capture Session Consent
                        val (consentValue, consentColor, consentComplete) = when (consentState) {
                            CaptureSessionConsentState.NOT_REQUESTED -> Triple("Not started", MaterialTheme.colorScheme.outline, false)
                            CaptureSessionConsentState.REQUESTING -> Triple("Requesting", MaterialTheme.colorScheme.secondary, false)
                            CaptureSessionConsentState.APPROVED -> Triple("Approved for this session", MaterialTheme.colorScheme.primary, true)
                            CaptureSessionConsentState.DENIED -> Triple("Denied", MaterialTheme.colorScheme.error, false)
                            CaptureSessionConsentState.ERROR -> Triple("Error", MaterialTheme.colorScheme.error, false)
                        }
                        DiagnosticRow(
                            label = "Capture session",
                            value = consentValue,
                            valueColor = consentColor,
                            isComplete = consentComplete
                        )

                        // Diagnostical row 4: Capture service
                        val (serviceValue, serviceColor, serviceComplete) = when (serviceState) {
                            CaptureServiceState.OFF -> Triple("Off", MaterialTheme.colorScheme.outline, false)
                            CaptureServiceState.STARTING -> Triple("Starting", MaterialTheme.colorScheme.secondary, false)
                            CaptureServiceState.ACTIVE -> Triple("Active", MaterialTheme.colorScheme.primary, true)
                            CaptureServiceState.STOPPED -> Triple("Stopped", MaterialTheme.colorScheme.outline, false)
                            CaptureServiceState.ERROR -> Triple("Error", MaterialTheme.colorScheme.error, false)
                        }
                        DiagnosticRow(
                            label = "Capture service",
                            value = serviceValue,
                            valueColor = serviceColor,
                            isComplete = serviceComplete
                        )

                        // Diagnostical row 5: Audio capture
                        val (captureValue, captureColor, captureComplete) = when (captureState) {
                            AudioCaptureState.NOT_BUILT -> Triple("Not built", MaterialTheme.colorScheme.outline, false)
                            AudioCaptureState.INITIALIZING -> Triple("Initializing", MaterialTheme.colorScheme.secondary, false)
                            AudioCaptureState.READY -> Triple("Ready", MaterialTheme.colorScheme.primary, true)
                            AudioCaptureState.CAPTURING -> Triple("Capturing", MaterialTheme.colorScheme.primary, true)
                            AudioCaptureState.AUDIO_DETECTED -> Triple("Audio detected", MaterialTheme.colorScheme.primary, true)
                            AudioCaptureState.NO_AUDIO_DETECTED -> Triple("No audio detected", MaterialTheme.colorScheme.error, false)
                            AudioCaptureState.BLOCKED_OR_UNSUPPORTED -> Triple("Source may not allow capture", MaterialTheme.colorScheme.error, false)
                            AudioCaptureState.ERROR -> Triple("Error", MaterialTheme.colorScheme.error, false)
                            AudioCaptureState.STOPPED -> Triple("Stopped", MaterialTheme.colorScheme.outline, false)
                        }
                        DiagnosticRow(
                            label = "Audio capture",
                            value = captureValue,
                            valueColor = captureColor,
                            isComplete = captureComplete
                        )

                        // Diagnostical row 6: Audio level
                        DiagnosticRow(
                            label = "Audio level",
                            value = String.format(java.util.Locale.US, "%.3f", rmsLevel),
                            valueColor = if (rmsLevel > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            isComplete = rmsLevel > 0.0
                        )

                        // Diagnostical row: Rolling buffer
                        val (bufferVal, bufferCol, bufferComplete) = when (bufferState) {
                            RollingBufferState.EMPTY -> Triple("Empty", MaterialTheme.colorScheme.outline, false)
                            RollingBufferState.FILLING -> Triple("Filling", MaterialTheme.colorScheme.secondary, true)
                            RollingBufferState.READY -> Triple("Ready", MaterialTheme.colorScheme.primary, true)
                            RollingBufferState.FROZEN -> Triple("Frozen", MaterialTheme.colorScheme.primary, true)
                            RollingBufferState.ERROR -> Triple("Error", MaterialTheme.colorScheme.error, false)
                            RollingBufferState.CLEARED -> Triple("Cleared", MaterialTheme.colorScheme.outline, false)
                        }
                        DiagnosticRow(
                            label = "Rolling buffer",
                            value = bufferVal,
                            valueColor = bufferCol,
                            isComplete = bufferComplete
                        )

                        // Diagnostical row: Buffer length
                        DiagnosticRow(
                            label = "Buffer length",
                            value = String.format(java.util.Locale.US, "%.1fs", bufferDurationSeconds),
                            valueColor = if (bufferDurationSeconds > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            isComplete = bufferDurationSeconds > 0.0
                        )

                        // Diagnostical row: Frozen samples
                        DiagnosticRow(
                            label = "Frozen samples",
                            value = lastFrozenSampleCount.toString(),
                            valueColor = if (lastFrozenSampleCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            isComplete = lastFrozenSampleCount > 0
                        )

                        // Diagnostical row: Prepared audio
                        val (prepValue, prepColor, prepComplete) = when (preparationState) {
                            AudioPreparationState.IDLE -> Triple("Idle", MaterialTheme.colorScheme.outline, false)
                            AudioPreparationState.NO_FROZEN_BUFFER -> Triple("No frozen buffer", MaterialTheme.colorScheme.outline, false)
                            AudioPreparationState.PREPARING -> Triple("Preparing", MaterialTheme.colorScheme.secondary, false)
                            AudioPreparationState.READY -> Triple("Ready", MaterialTheme.colorScheme.primary, true)
                            AudioPreparationState.ERROR -> Triple("Error", MaterialTheme.colorScheme.error, false)
                        }
                        DiagnosticRow(
                            label = "Prepared audio",
                            value = prepValue,
                            valueColor = prepColor,
                            isComplete = prepComplete
                        )

                        // Diagnostical row: Prepared duration
                        DiagnosticRow(
                            label = "Prepared duration",
                            value = String.format(java.util.Locale.US, "%.1fs", preparedDurationSeconds),
                            valueColor = if (preparedDurationSeconds > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            isComplete = preparedDurationSeconds > 0.0
                        )

                        // Diagnostical row: Prepared samples
                        DiagnosticRow(
                            label = "Prepared samples",
                            value = preparedSampleCount.toString(),
                            valueColor = if (preparedSampleCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            isComplete = preparedSampleCount > 0
                        )

                        // Diagnostical row: Whisper Model
                        val hasModel = ModelPathResolver.doesModelExist(context)
                        DiagnosticRow(
                            label = "Whisper Model",
                            value = if (hasModel) "Found" else "Missing",
                            valueColor = if (hasModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            isComplete = hasModel,
                            modifier = Modifier.testTag("diagnostic_whisper_model")
                        )

                        // Diagnostical row: Engine
                        val (engineValue, engineColor, engineComplete) = when (engineMode) {
                            TranscriptionEngineMode.REMOTE_ENDPOINT -> Triple("Cloud endpoint", MaterialTheme.colorScheme.primary, true)
                            TranscriptionEngineMode.NATIVE_WHISPER -> Triple("Native Whisper", MaterialTheme.colorScheme.primary, true)
                            TranscriptionEngineMode.DEBUG_STUB -> Triple("Debug stub", MaterialTheme.colorScheme.secondary, true)
                            TranscriptionEngineMode.NOT_AVAILABLE -> Triple("Not available", MaterialTheme.colorScheme.error, false)
                        }
                        DiagnosticRow(
                            label = "Engine",
                            value = engineValue,
                            valueColor = engineColor,
                            isComplete = engineComplete,
                            modifier = Modifier.testTag("diagnostic_transcription_engine")
                        )

                        // Diagnostical row: Transcription state
                        val (transcriptionValue, transcriptionColor, transcriptionComplete) = when (transcriptionState) {
                            TranscriptionState.IDLE -> Triple("Idle", MaterialTheme.colorScheme.outline, false)
                            TranscriptionState.MODEL_MISSING -> Triple("Model missing", MaterialTheme.colorScheme.error, false)
                            TranscriptionState.MODEL_LOADING -> Triple("Model loading", MaterialTheme.colorScheme.secondary, false)
                            TranscriptionState.READY -> Triple("Ready", MaterialTheme.colorScheme.primary, true)
                            TranscriptionState.TRANSCRIBING -> Triple("Transcribing", MaterialTheme.colorScheme.secondary, false)
                            TranscriptionState.SUCCESS -> Triple("Success", MaterialTheme.colorScheme.primary, true)
                            TranscriptionState.ERROR -> Triple("Error", MaterialTheme.colorScheme.error, false)
                        }
                        DiagnosticRow(
                            label = "Transcription",
                            value = transcriptionValue,
                            valueColor = transcriptionColor,
                            isComplete = transcriptionComplete,
                            modifier = Modifier.testTag("diagnostic_transcription_state")
                        )

                        // Diagnostical row: Transcript length
                        DiagnosticRow(
                            label = "Transcript length",
                            value = if (transcriptText.isNotEmpty()) "${transcriptText.length} chars" else "0 chars",
                            valueColor = if (transcriptText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            isComplete = transcriptText.isNotEmpty(),
                            modifier = Modifier.testTag("diagnostic_transcript_length")
                        )

                        // Diagnostical row: Transcription time
                        val formattedTimeValue = if (transcriptionDurationMs != null) {
                            String.format(java.util.Locale.US, "%.1fs", transcriptionDurationMs!! / 1000.0)
                        } else {
                            "--"
                        }
                        DiagnosticRow(
                            label = "Transcription time",
                            value = formattedTimeValue,
                            valueColor = if (transcriptionDurationMs != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            isComplete = transcriptionDurationMs != null,
                            modifier = Modifier.testTag("diagnostic_transcription_time")
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Latest transcript diagnostic card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("latest_transcript_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Latest transcript",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("latest_transcript_title")
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Text(
                            text = if (transcriptText.isNotEmpty()) transcriptText else "No local transcript available. Freeze and prepare audio, then click transcribe.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (transcriptText.isNotEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.testTag("latest_transcript_body")
                        )
                    }
                }

                // Step 11 notice and explanation card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Step 11 Advisory info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Step 11 connects bubble tap to local transcription and shows a simple transcript result.",
                            style = Modifier.testTag("step_11_explanation_text").let { MaterialTheme.typography.bodySmall },
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.testTag("step_11_explanation_text")
                        )
                    }
                }

                // CTAs organized neatly
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Button 1: Start/Request Capture Session
                    OutlinedButton(
                        onClick = {
                            try {
                                MediaProjectionConsentStateHolder.markRequesting()
                                val intent = mediaProjectionPermissionHelper.createCaptureIntent()
                                mediaProjectionLauncher.launch(intent)
                            } catch (e: Exception) {
                                MediaProjectionConsentStateHolder.markError()
                                coroutineScope.launch {
                                    Toast.makeText(context, "Error starting capture session: ${e.message}", Toast.LENGTH_SHORT).show()
                                    snackbarHostState.showSnackbar("Error starting capture: ${e.localizedMessage}")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_capture_session_button")
                    ) {
                        Text(
                            text = "Start Capture Session",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Button 2: Start / Stop Capture Service CTA
                    OutlinedButton(
                        onClick = {
                            if (isServiceActive) {
                                // Stop Capture Service
                                val intent = Intent(context, AudioCaptureService::class.java).apply {
                                    action = AudioCaptureService.ACTION_STOP_CAPTURE_SERVICE
                                }
                                context.stopService(intent)
                                coroutineScope.launch {
                                    Toast.makeText(context, "Capture service stopped.", Toast.LENGTH_SHORT).show()
                                    snackbarHostState.showSnackbar("Capture service stopped.")
                                }
                            } else {
                                // Start Capture Service
                                val resultCode = MediaProjectionConsentStateHolder.getLatestResultCode()
                                val data = MediaProjectionConsentStateHolder.getLatestData()

                                if (resultCode == null || data == null) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Approve a capture session first.", Toast.LENGTH_SHORT).show()
                                        snackbarHostState.showSnackbar("Approve a capture session first.")
                                    }
                                } else {
                                    val intent = Intent(context, AudioCaptureService::class.java).apply {
                                        action = AudioCaptureService.ACTION_START_CAPTURE_SERVICE
                                        putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                                        putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data)
                                    }
                                    try {
                                        ContextCompat.startForegroundService(context, intent)
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Capture service started. Audio capture will be added next.", Toast.LENGTH_SHORT).show()
                                            snackbarHostState.showSnackbar("Capture service started. Audio capture will be added next.")
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        CaptureServiceStateHolder.markError()
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Failed to start capture service.", Toast.LENGTH_SHORT).show()
                                            snackbarHostState.showSnackbar("Failed to start capture service.")
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_capture_service_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isServiceActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (isServiceActive) "Stop Capture Service" else "Start Capture Service",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Button 3: Start / Stop Float button CTA (for Step 3 bubble)
                    OutlinedButton(
                        onClick = {
                            if (isBubbleActive) {
                                // Stop Bubble
                                val intent = Intent(context, FloatingBubbleService::class.java)
                                context.stopService(intent)
                                coroutineScope.launch {
                                    Toast.makeText(context, "Floating bubble stopped.", Toast.LENGTH_SHORT).show()
                                    snackbarHostState.showSnackbar("Floating bubble stopped.")
                                }
                            } else {
                                // Start Bubble
                                if (!Settings.canDrawOverlays(context)) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Floating button permission is not enabled yet.", Toast.LENGTH_SHORT).show()
                                        snackbarHostState.showSnackbar("Floating button permission is not enabled yet.")
                                    }
                                } else {
                                    val intent = Intent(context, FloatingBubbleService::class.java)
                                    context.startService(intent)
                                    coroutineScope.launch {
                                        Toast.makeText(context, "Floating bubble is active.", Toast.LENGTH_SHORT).show()
                                        snackbarHostState.showSnackbar("Floating bubble is active.")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_floating_capture_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isBubbleActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (isBubbleActive) "Stop Floating Bubble" else "Start Floating Bubble",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Button 4: Freeze Current Buffer CTA
                    OutlinedButton(
                        onClick = {
                            val result = com.example.capture.BufferFreezeController.freezeCurrentBuffer()
                            val message = when (result) {
                                com.example.capture.BufferFreezeResult.SUCCESS_FULL_BUFFER -> {
                                    "Recent audio captured. Transcription will be added later."
                                }
                                com.example.capture.BufferFreezeResult.SUCCESS_SHORT_BUFFER -> {
                                    "Recent audio captured, but it is shorter than 45 seconds."
                                }
                                com.example.capture.BufferFreezeResult.NO_CAPTURE_SERVICE -> {
                                    "Start capture service first."
                                }
                                com.example.capture.BufferFreezeResult.NO_BUFFER_AVAILABLE -> {
                                    "No recent audio buffer available yet."
                                }
                                com.example.capture.BufferFreezeResult.ERROR -> {
                                    "Could not capture recent audio."
                                }
                            }
                            coroutineScope.launch {
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("freeze_current_buffer_button")
                    ) {
                        Text(
                            text = "Freeze Current Buffer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Button 5: Prepare Frozen Audio CTA
                    OutlinedButton(
                        onClick = {
                            val result = AudioPreparationController.prepareLatestFrozenAudio(includeWavBytes = true)
                            val message = when (result) {
                                AudioPreparationResult.SUCCESS -> {
                                    "Frozen audio prepared for future transcription."
                                }
                                AudioPreparationResult.NO_FROZEN_BUFFER -> {
                                    "Freeze recent audio first."
                                }
                                AudioPreparationResult.TOO_SHORT -> {
                                   "Frozen audio is too short to prepare."
                                }
                                AudioPreparationResult.ERROR -> {
                                    "Could not prepare frozen audio."
                                }
                            }
                            coroutineScope.launch {
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("prepare_frozen_audio_button")
                    ) {
                        Text(
                            text = "Prepare Frozen Audio",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Button 6: Transcribe Prepared Audio CTA
                    val isButtonDisabled = transcriptionState == TranscriptionState.MODEL_LOADING ||
                            transcriptionState == TranscriptionState.TRANSCRIBING
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val result = TranscriptionController.transcribePreparedAudio(context)
                                val message = when (result) {
                                    TranscriptionResult.SUCCESS -> {
                                        "Transcription complete."
                                    }
                                    TranscriptionResult.NO_PREPARED_AUDIO -> {
                                        "Prepare frozen audio first."
                                    }
                                    TranscriptionResult.MODEL_MISSING -> {
                                        "Local transcription model not found."
                                    }
                                    TranscriptionResult.AUTH_REQUIRED -> {
                                        "Authentication expired. Reopen ClipScribe and try again."
                                    }
                                    TranscriptionResult.QUOTA_EXCEEDED -> {
                                        "Free transcript limit reached for today."
                                    }
                                    TranscriptionResult.ERROR -> {
                                        if (!com.example.transcription.TranscriptionController.isTranscriptionAvailable()) {
                                            "Local transcription engine is not available on this build."
                                        } else {
                                            "Could not transcribe prepared audio."
                                        }
                                    }
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        enabled = !isButtonDisabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("transcribe_prepared_audio_button")
                    ) {
                        Text(
                            text = if (transcriptionState == TranscriptionState.TRANSCRIBING) {
                                "Transcribing..."
                            } else if (transcriptionState == TranscriptionState.MODEL_LOADING) {
                                "Loading Model..."
                            } else {
                                "Transcribe Prepared Audio"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Main User-Facing MVP view
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_mvp_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!setupComplete) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        } else if (captureModeState == CaptureModeState.ACTIVE) {
                            Color(0xFF10B981).copy(alpha = 0.08f) // soft emerald active background
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) // calm blue/indigo ready container
                        }
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (!setupComplete) {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        } else if (captureModeState == CaptureModeState.ACTIVE) {
                            Color(0xFF10B981).copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        if (!setupComplete) {
                            // Setup Incomplete State Illustration
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚠️", fontSize = 36.sp)
                            }

                            Text(
                                text = "Setup incomplete",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("user_status_text")
                            )

                            Text(
                                text = "Enable the basics so the floating bubble can work.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("user_help_text")
                            )

                            Text(
                                text = "Your audio stays on your phone.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("privacy_setup_text")
                            )

                            Button(
                                onClick = { onNavigateToPermissions() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("complete_setup_button"),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                            ) {
                                Text(text = "Complete setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val isCaptureActive = captureModeState == CaptureModeState.ACTIVE
                            
                            // Active or Ready state illustration
                            if (isCaptureActive) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🟢", fontSize = 36.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🎙️", fontSize = 36.sp)
                                }
                            }

                            Text(
                                text = if (isCaptureActive) "Capture mode active" else "Ready",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isCaptureActive) Color(0xFF0F766E) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("user_status_text")
                            )

                            Text(
                                text = if (isCaptureActive) {
                                    "Open another app and tap the floating bubble when you hear something useful."
                                } else {
                                    "Start capture mode before opening a lecture, video, or podcast."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("user_help_text")
                            )

                            if (engineMode == TranscriptionEngineMode.REMOTE_ENDPOINT) {
                                freeTierUsage.dailyRemaining?.let { remaining ->
                                    Text(
                                        text = if (remaining == 1) {
                                            "1 free capture left today"
                                        } else {
                                            "$remaining free captures left today"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("free_uses_remaining_text")
                                    )
                                }
                            }

                            if (isCaptureActive) {
                                Button(
                                    onClick = {
                                        CaptureModeController.stopCaptureMode(context)
                                        coroutineScope.launch {
                                            Toast.makeText(context, "Capture mode stopped.", Toast.LENGTH_SHORT).show()
                                            snackbarHostState.showSnackbar("Capture mode stopped.")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("stop_capture_mode_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    Text(text = "Stop Capture Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        try {
                                            CaptureModeStateHolder.markStarting()
                                            val intent = mediaProjectionPermissionHelper.createCaptureIntent()
                                            mediaProjectionLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            CaptureModeStateHolder.markError()
                                            coroutineScope.launch {
                                                Toast.makeText(context, "Error starting capture: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .testTag("start_capture_mode_button"),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    Text(text = "Start Capture Mode", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Developer Mode switch right at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Developer Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = isDevModeEnabled,
                    onCheckedChange = { DeveloperModeStateHolder.setDeveloperModeEnabled(it) },
                    modifier = Modifier.testTag("developer_mode_toggle")
                )
            }
        }
    }

    TranscriptResultSheet(
        state = transcriptResultState,
        onDismiss = { TranscriptResultStateHolder.dismiss() }
    )
}

@Composable
fun DiagnosticRow(
    label: String,
    value: String,
    valueColor: Color,
    isComplete: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isComplete) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.extraSmall
                        )
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
