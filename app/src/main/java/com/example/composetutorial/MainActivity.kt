package com.example.composetutorial

import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream

@Serializable object DisplayRoute
@Serializable object EditRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(applicationContext)
        val userDao = db.userDao()

        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = DisplayRoute) {
                composable<DisplayRoute> {
                    val user = userDao.getUser() ?: UserProfile(name = "No name set", imagePath = null)
                    DisplayScreen(user) { navController.navigate(EditRoute) }
                }
                composable<EditRoute> {
                    EditScreen(
                        onSave = { name, uri ->
                            val path = uri?.let { saveImage(it) }
                            userDao.saveUser(UserProfile(name = name, imagePath = path))
                            navController.navigate(DisplayRoute) {
                                popUpTo(DisplayRoute) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun saveImage(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(filesDir, "profile.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
        return file.absolutePath
    }
}

@Composable
fun DisplayScreen(user: UserProfile, onEdit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(
            model = user.imagePath ?: "https://via.placeholder.com/150",
            contentDescription = null,
            modifier = Modifier.size(150.dp)
        )
        Text(text = user.name, style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onEdit) { Text("Edit Profile") }
    }
}

@Composable
fun EditScreen(onSave: (String, Uri?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { imageUri = it }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        Button(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text("Pick Image")
        }
        Button(onClick = { onSave(name, imageUri) }) { Text("Save") }
    }
}