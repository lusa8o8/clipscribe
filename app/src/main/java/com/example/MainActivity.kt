package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auth.FirebaseAuthStateHolder
import com.example.storage.SavedTranscriptStateHolder
import com.example.ui.HomeScreen
import com.example.ui.PermissionChecklistScreen
import com.example.ui.RecentTranscriptsScreen
import com.example.ui.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    FirebaseAuthStateHolder.startAnonymousSignIn(applicationContext)
    enableEdgeToEdge()

    val prefs = getSharedPreferences("clipscribe_prefs", Context.MODE_PRIVATE)
    val setupComplete = prefs.getBoolean("setup_complete", false)
    val startDestination = if (setupComplete) "home" else "welcome"
    val markSetupComplete: () -> Unit = {
      prefs.edit().putBoolean("setup_complete", true).apply()
    }

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val navController = rememberNavController()
          NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
          ) {
            composable("welcome") {
              WelcomeScreen(
                onNavigateNext = {
                  navController.navigate("permissions")
                }
              )
            }
            composable("permissions") {
              PermissionChecklistScreen(
                onPermissionsGranted = {
                  markSetupComplete()
                  navController.navigate("home") {
                    popUpTo("welcome") { inclusive = true }
                  }
                }
              )
            }
            composable("home") {
              HomeScreen(
                onNavigateToPermissions = {
                  navController.navigate("permissions")
                },
                onNavigateToHistory = {
                  navController.navigate("history")
                }
              )
            }
            composable("history") {
              val transcripts by SavedTranscriptStateHolder.transcripts.collectAsState()
              val coroutineScope = rememberCoroutineScope()
              val context = LocalContext.current
              RecentTranscriptsScreen(
                transcripts = transcripts,
                onDelete = { id ->
                  coroutineScope.launch {
                    SavedTranscriptStateHolder.deleteTranscript(id, context)
                  }
                },
                onNavigateBack = {
                  navController.popBackStack()
                }
              )
            }
          }
        }
      }
    }
  }
}
