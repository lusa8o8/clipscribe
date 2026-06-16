package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auth.FirebaseAuthStateHolder
import com.example.ui.HomeScreen
import com.example.ui.PermissionChecklistScreen
import com.example.ui.WelcomeScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    FirebaseAuthStateHolder.startAnonymousSignIn(applicationContext)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val navController = rememberNavController()
          NavHost(
            navController = navController,
            startDestination = "welcome",
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
                }
              )
            }
          }
        }
      }
    }
  }
}
