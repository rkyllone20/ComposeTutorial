package com.example.composetutorial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.composetutorial.ui.theme.ComposeTutorialTheme
import kotlinx.serialization.Serializable

// Using @Serializable objects for type-safe navigation
@Serializable
object MainScreen

@Serializable
object InfoScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeTutorialTheme {
                // Initializing navigation controller
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = MainScreen
                ) {
                    // Main View (HW1)
                    composable<MainScreen> {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            GreetingImage(
                                message = "Happy Birthday!",
                                from = "- Emma",
                                modifier = Modifier.padding(innerPadding),
                                onNavigateToInfo = {
                                    navController.navigate(InfoScreen)
                                }
                            )
                        }
                    }

                    // Secondary View (HW2)
                    composable<InfoScreen> {
                        InfoScreen(
                            onBackToMain = {
                                // Preventing circular navigation
                                // Using popUpTo with inclusive = true clears the backstack
                                // up to MainScreen and replaces it, ensuring the back button
                                // exits the app from the main view instead of looping.
                                navController.navigate(MainScreen) {
                                    popUpTo(MainScreen) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GreetingImage(
    message: String,
    from: String,
    onNavigateToInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isClicked by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HW1: Interactive profile picture
        Image(
            painter = painterResource(R.drawable.profile_picture),
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(250.dp)
                .padding(bottom = 24.dp)
                .clickable { isClicked = !isClicked }
        )
        Text(
            text = if (isClicked) "Surprise!!!" else message,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = from,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )

        // HW2: Navigation button to move to the next screen
        Button(
            onClick = onNavigateToInfo,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("Go to Info Screen")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // HW1: List of greeting cards
        repeat(5) { index ->
            Card(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Greeting no. ${index + 1}",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun InfoScreen(onBackToMain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Info View",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "This is the second view for the assignment.",
            modifier = Modifier.padding(vertical = 16.dp),
            textAlign = TextAlign.Center
        )

        // HW2: Button to return to the main view
        Button(onClick = onBackToMain) {
            Text("Back to Main Page")
        }
    }
}