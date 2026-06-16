package com.example.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.permissions.OverlayPermissionHelper
import com.example.permissions.PermissionManager
import com.example.ui.components.PermissionChecklistItem

@Composable
fun PermissionChecklistScreen(
    onPermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize the permission manager & overlay helper
    val permissionManager = remember { PermissionManager(context) }
    val overlayPermissionHelper = remember { OverlayPermissionHelper(context) }
    
    val state by permissionManager.permissionState.collectAsState()

    // Activity Result Launcher for Audio Permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionManager.updatePermissionState()
    }

    // Activity Result Launcher for Notification Permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        permissionManager.updatePermissionState()
    }

    // Recheck permissions dynamically whenever user returns to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionManager.updatePermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Single-view vertical layout
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Step 2 main headers
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Set up ClipScribe",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("setup_title")
            )
            Text(
                text = "Enable the basics so the capture bubble can work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("setup_subtitle")
            )
        }

        // Privacy note card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = "V1 is local-first. No account, no backend, no cloud transcription.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(14.dp)
                    .testTag("privacy_text"),
                textAlign = TextAlign.Start
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Checklist Item 1: Floating Button (Overlay Option)
        PermissionChecklistItem(
            title = "Floating button",
            description = "Shows the capture bubble over apps like YouTube, TikTok, Spotify, and your browser.",
            statusText = if (state.overlayGranted) "Enabled" else "Not enabled",
            isCompleted = state.overlayGranted,
            actionButtonText = "Enable floating button",
            onActionButtonClick = {
                val intent = overlayPermissionHelper.createOverlayPermissionIntent()
                if (intent != null) {
                    context.startActivity(intent)
                }
            }
        )

        // Checklist Item 2: Capture Notification
        val isAtLeastAndroid13 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val notificationStatusText = if (!isAtLeastAndroid13) {
            "Not needed on this device"
        } else if (state.notificationGrantedOrNotRequired) {
            "Enabled"
        } else {
            "Not enabled"
        }

        PermissionChecklistItem(
            title = "Capture notification",
            description = "Shows when ClipScribe is active and gives you a Stop button.",
            statusText = notificationStatusText,
            isCompleted = state.notificationGrantedOrNotRequired,
            actionButtonText = "Enable notification",
            onActionButtonClick = {
                if (isAtLeastAndroid13) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            hasAction = isAtLeastAndroid13 // Show button only on Android 13+
        )

        // Checklist Item 3: Audio processing / Record Audio
        PermissionChecklistItem(
            title = "Audio processing",
            description = "Allows ClipScribe to process supported audio for transcription.",
            statusText = if (state.recordAudioGranted) "Enabled" else "Not enabled",
            isCompleted = state.recordAudioGranted,
            actionButtonText = "Enable audio processing",
            onActionButtonClick = {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        // Checklist Item 4: Media Projection (Session Capture explanation only)
        PermissionChecklistItem(
            title = "Audio capture session",
            description = "When you start capture mode, Android will ask for permission for that session.",
            statusText = "Asked when capture starts",
            isCompleted = false,
            actionButtonText = null,
            onActionButtonClick = {},
            hasAction = false
        )

        Spacer(modifier = Modifier.weight(1f))

        // Readiness layout
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (state.allGranted) {
                    "Setup complete. You are ready for the next step."
                } else {
                    "Finish setup to continue."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (state.allGranted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.testTag("setup_status_text")
            )

            Button(
                onClick = { if (state.allGranted) onPermissionsGranted() },
                enabled = state.allGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("permissions_continue_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Continue to Home",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
