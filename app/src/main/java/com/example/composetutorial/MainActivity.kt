package com.example.composetutorial

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream

@Serializable object MapRoute
@Serializable object DisplayRoute
@Serializable object EditRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(applicationContext)
        val userDao = db.userDao()

        setContent {
            val navController = rememberNavController()

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NavHost(navController = navController, startDestination = MapRoute) {

                        composable<MapRoute> {
                            MapScreen(
                                onNavigateToProfile = { navController.navigate(DisplayRoute) }
                            )
                        }

                        composable<DisplayRoute> {
                            // Fetch user from database and pass it to DisplayScreen
                            val userState = remember {
                                mutableStateOf(userDao.getUser() ?: UserProfile(name = "New User", imagePath = null))
                            }
                            DisplayScreen(
                                user = userState.value,
                                onEdit = { navController.navigate(EditRoute) },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<EditRoute> {
                            EditScreen(onSave = { name, uri ->
                                val path = uri?.let { saveImageLocally(it) }
                                userDao.saveUser(UserProfile(name = name, imagePath = path))
                                navController.navigate(DisplayRoute)
                            })
                        }
                    }
                }
            }
        }
    }

    private fun saveImageLocally(uri: Uri): String {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(filesDir, "profile_pic_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("MainActivity", "Error saving image", e)
            ""
        }
    }
}

@Composable
fun MapScreen(onNavigateToProfile: () -> Unit) {
    val oulu = LatLng(65.0121, 25.4651)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(oulu, 12f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            // Test marker for city center
            Marker(
                state = MarkerState(position = oulu),
                title = "Oulu",
                snippet = "Kaikki toimii!"
            )
        }

        // Profile button
        Button(
            onClick = onNavigateToProfile,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text("Profile")
        }
    }
}

// DisplayScreen and EditScreen
@Composable
fun DisplayScreen(user: UserProfile, onEdit: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) context.startService(Intent(context, SensorService::class.java))
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = user.imagePath ?: "https://via.placeholder.com/150",
            contentDescription = null,
            modifier = Modifier.size(150.dp)
        )
        Text(text = user.name, style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onEdit) { Text("Edit Profile") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onBack) { Text("Back to Map") }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startService(Intent(context, SensorService::class.java))
            }
        }) {
            Text("Start Sensor Monitoring")
        }
    }
}

@Composable
fun EditScreen(onSave: (String, Uri?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { imageUri = it }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text("Pick Image")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onSave(name, imageUri) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
    }
}