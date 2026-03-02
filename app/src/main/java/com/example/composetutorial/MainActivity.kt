package com.example.composetutorial

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- REITIT ---
@Serializable object SetupRoute
@Serializable object MapRoute
@Serializable object DisplayRoute
@Serializable object EditRoute
// LogVisit uses a string argument for the park name
const val LOG_VISIT_ROUTE = "log_visit/{parkName}"
fun logVisitRoute(parkName: String) = "log_visit/$parkName"

// --- LIPAS DATA MALLIT ---
@Serializable
data class LipasPlace(
    val sportsPlaceId: Int,
    val name: String,
    val typeName: String,
    val lat: Double,
    val lon: Double
)

// --- YÖNÄKYMÄN KARTTASTIILI ---
private const val NIGHT_MAP_STYLE = """
[
  {"elementType":"geometry","stylers":[{"color":"#242f3e"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#746855"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#242f3e"}]},
  {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#263c3f"}]},
  {"featureType":"poi.park","elementType":"labels.text.fill","stylers":[{"color":"#6b9a76"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#38414e"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"#212a37"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9ca5b3"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#746855"}]},
  {"featureType":"road.highway","elementType":"geometry.stroke","stylers":[{"color":"#1f2835"}]},
  {"featureType":"road.highway","elementType":"labels.text.fill","stylers":[{"color":"#f3d19c"}]},
  {"featureType":"transit","elementType":"geometry","stylers":[{"color":"#2f3948"}]},
  {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"#d59563"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#17263c"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#515c6d"}]},
  {"featureType":"water","elementType":"labels.text.stroke","stylers":[{"color":"#17263c"}]}
]
"""

private const val CHANNEL_ID = "night_mode_channel"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Yö/päivätila", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Ilmoitukset kartan tilan vaihtumisesta" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

fun sendModeNotification(context: Context, isNightMode: Boolean) {
    val title = if (isNightMode) "🌙 Yötila käytössä" else "☀️ Päivätila käytössä"
    val text  = if (isNightMode) "Kartta vaihdettu yönäkymään." else "Kartta vaihdettu päivänäkymään."
    val pendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    context.getSystemService(NotificationManager::class.java).notify(42, notification)
}

// --- MAIN ACTIVITY ---

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        createNotificationChannel(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val userDao = db.userDao()
        val visitDao = db.visitDao()

        setContent {
            val navController = rememberNavController()
            val existingUser = remember { userDao.getUser() }
            val startDestination = if (existingUser != null) MapRoute else SetupRoute

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NavHost(navController = navController, startDestination = startDestination) {

                        composable<SetupRoute> {
                            SetupScreen(onComplete = { name, uri ->
                                val path = uri?.let { saveImageLocally(it) }
                                userDao.saveUser(UserProfile(name = name, imagePath = path))
                                navController.navigate(MapRoute) {
                                    popUpTo<SetupRoute> { inclusive = true }
                                }
                            })
                        }

                        composable<MapRoute> {
                            val userState = remember {
                                mutableStateOf(userDao.getUser() ?: UserProfile(name = "", imagePath = null))
                            }
                            MapScreen(
                                user = userState.value,
                                onNavigateToProfile = { navController.navigate(DisplayRoute) },
                                onLogVisit = { parkName ->
                                    navController.navigate(logVisitRoute(parkName))
                                }
                            )
                        }

                        composable<DisplayRoute> {
                            val userState = remember {
                                mutableStateOf(userDao.getUser() ?: UserProfile(name = "", imagePath = null))
                            }
                            val visits = remember { mutableStateOf(visitDao.getAllVisits()) }
                            DisplayScreen(
                                user = userState.value,
                                visits = visits.value,
                                onEdit = { navController.navigate(EditRoute) },
                                onBack = { navController.popBackStack() },
                                onDeleteVisit = { visitId ->
                                    visitDao.deleteVisit(visitId)
                                    visits.value = visitDao.getAllVisits()
                                }
                            )
                        }

                        composable<EditRoute> {
                            EditScreen(onSave = { name, uri ->
                                val path = uri?.let { saveImageLocally(it) }
                                userDao.saveUser(UserProfile(name = name, imagePath = path))
                                navController.popBackStack()
                            })
                        }

                        composable(
                            route = LOG_VISIT_ROUTE,
                            arguments = listOf(navArgument("parkName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val parkName = backStackEntry.arguments?.getString("parkName") ?: ""
                            LogVisitScreen(
                                parkName = parkName,
                                onSave = { rating, note, imagePath ->
                                    visitDao.insertVisit(
                                        Visit(
                                            placeName = parkName,
                                            rating = rating,
                                            note = note,
                                            imagePath = imagePath
                                        )
                                    )
                                    navController.popBackStack()
                                },
                                onBack = { navController.popBackStack() }
                            )
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

// --- APUFUNKTIO: Koordinaattien haku ---
fun extractLatLon(obj: com.google.gson.JsonObject): Pair<Double, Double>? {
    val wgs84 = obj.getAsJsonObject("location")?.getAsJsonObject("coordinates")?.getAsJsonObject("wgs84")
    val simpleLat = wgs84?.get("lat")?.asDouble
    val simpleLon = wgs84?.get("lon")?.asDouble
    if (simpleLat != null && simpleLon != null) return Pair(simpleLat, simpleLon)

    val geometry = obj.getAsJsonObject("location")?.getAsJsonObject("geometries")
        ?.getAsJsonArray("features")?.get(0)?.asJsonObject?.getAsJsonObject("geometry")
    val geomType = geometry?.get("type")?.asString
    val coords = geometry?.getAsJsonArray("coordinates")

    return when (geomType) {
        "Point" -> {
            val lon = coords?.get(0)?.asDouble
            val lat = coords?.get(1)?.asDouble
            if (lat != null && lon != null) Pair(lat, lon) else null
        }
        "Polygon" -> {
            val lon = coords?.get(0)?.asJsonArray?.get(0)?.asJsonArray?.get(0)?.asDouble
            val lat = coords?.get(0)?.asJsonArray?.get(0)?.asJsonArray?.get(1)?.asDouble
            if (lat != null && lon != null) Pair(lat, lon) else null
        }
        else -> null
    }
}

// --- NÄKYMÄT ---

// --- REUSABLE PHOTO PICKER SHEET ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPickerSheet(
    currentUri: Uri?,
    isCircle: Boolean = true,
    onUriSelected: (Uri?) -> Unit
) {
    val context = LocalContext.current
    var showSheet by remember { mutableStateOf(false) }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onUriSelected(uri) }

    // Camera setup
    val cameraFile = remember { File(context.filesDir, "profile_cam_temp.jpg") }
    val cameraUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", cameraFile)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK &&
            cameraFile.exists() && cameraFile.length() > 0) {
            val permanent = File(context.filesDir, "profile_${System.currentTimeMillis()}.jpg")
            cameraFile.copyTo(permanent, overwrite = true)
            onUriSelected(Uri.fromFile(permanent))
        }
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            cameraLauncher.launch(intent)
        }
    }

    fun launchCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (context.checkSelfPermission(android.Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(intent)
        } else {
            cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Profile picture display
    val shape = if (isCircle) CircleShape else RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .then(if (isCircle) Modifier.size(120.dp) else Modifier.fillMaxWidth().height(200.dp))
            .clip(shape)
            .border(2.dp, MaterialTheme.colorScheme.primary, shape)
            .background(Color(0xFFEEEEEE))
            .clickable { showSheet = true },
        contentAlignment = Alignment.Center
    ) {
        if (currentUri != null) {
            AsyncImage(
                model = currentUri,
                contentDescription = "Kuva",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
            // Edit overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x44000000), shape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(32.dp))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Lisää kuva",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Source picker bottom sheet
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp)
            ) {
                Text("Valitse kuvan lähde", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Camera option
                    OutlinedCard(
                        modifier = Modifier.weight(1f).clickable {
                            showSheet = false
                            launchCamera()
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Kamera", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    // Gallery option
                    OutlinedCard(
                        modifier = Modifier.weight(1f).clickable {
                            showSheet = false
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Galleria", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(onComplete: (String, Uri?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Tervetuloa!", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Aseta ensin profiilisi", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(40.dp))

        PhotoPickerSheet(
            currentUri = imageUri,
            isCircle = true,
            onUriSelected = { imageUri = it }
        )

        Spacer(modifier = Modifier.height(24.dp))
        TextField(value = name, onValueChange = { name = it }, label = { Text("Nimesi") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = { if (name.isNotBlank()) onComplete(name, imageUri) },
            enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Aloita")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(user: UserProfile, onNavigateToProfile: () -> Unit, onLogVisit: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val oulu = LatLng(65.0121, 25.4651)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(oulu, 12f)
    }

    var places by remember { mutableStateOf<List<LipasPlace>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isNightMode by remember { mutableStateOf(false) }
    val prevNightMode = remember { mutableStateOf<Boolean?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

    // Bottom sheet state for selected park
    var selectedPlace by remember { mutableStateOf<LipasPlace?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    val filteredPlaces = remember(searchQuery, places) {
        if (searchQuery.isBlank()) emptyList()
        else places.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("fi", "FI"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sano puiston nimi...")
        }
    }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull() ?: return@rememberLauncherForActivityResult
        searchQuery = spokenText
        showSuggestions = true
    }

    LaunchedEffect(isNightMode) {
        if (prevNightMode.value != null && prevNightMode.value != isNightMode) {
            sendModeNotification(context, isNightMode)
        }
        prevNightMode.value = isNightMode
    }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val lux = event?.values?.get(0) ?: return
                isNightMode = lux < 10f
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        lightSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    LaunchedEffect(Unit) {
        try {
            val results = mutableListOf<LipasPlace>()
            for ((typeCode, typeName) in listOf(101 to "Lähipuisto", 103 to "Ulkoilualue")) {
                val response = RetrofitInstance.api.getSportsPlaces(typeCode = typeCode)
                val items = response.getAsJsonArray("items")
                items?.forEach { element ->
                    val obj = element.asJsonObject
                    val name = obj.get("name")?.asString ?: "Nimetön"
                    val id = obj.get("lipas-id")?.asInt ?: 0
                    val (lat, lon) = extractLatLon(obj) ?: return@forEach
                    results.add(LipasPlace(id, name, typeName, lat, lon))
                }
            }
            places = results
        } catch (e: HttpException) {
            errorMessage = "HTTP ${e.code()}: ${e.response()?.errorBody()?.string()}"
        } catch (e: Exception) {
            errorMessage = "Virhe: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    // Bottom sheet for selected park
    if (showSheet && selectedPlace != null) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedPlace!!.name,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedPlace!!.typeName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        showSheet = false
                        onLogVisit(selectedPlace!!.name)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kirjaa käynti")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sulje")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Haetaan puistoja...")
            }
        } else if (errorMessage != null) {
            Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(16.dp))
        } else {
            val mapProperties = remember(isNightMode) {
                MapProperties(mapStyleOptions = if (isNightMode) MapStyleOptions(NIGHT_MAP_STYLE) else null)
            }
            GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState,
                properties = mapProperties) {
                places.forEach { place ->
                    Marker(
                        state = rememberMarkerState(position = LatLng(place.lat, place.lon)),
                        title = place.name,
                        snippet = place.typeName,
                        onClick = {
                            selectedPlace = place
                            showSheet = true
                            true
                        }
                    )
                }
            }
        }

        // Search bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(28.dp))
                    .background(
                        if (isNightMode) Color(0xFF2C2C2C) else Color.White,
                        RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null,
                    tint = if (isNightMode) Color(0xFFAAAAAA) else Color(0xFF666666))
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; showSuggestions = it.isNotBlank() },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = if (isNightMode) Color.White else Color.Black,
                        fontSize = 16.sp
                    ),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Hae puistoja...",
                                color = if (isNightMode) Color(0xFF888888) else Color(0xFF999999),
                                fontSize = 16.sp)
                        }
                        inner()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; showSuggestions = false }) {
                        Icon(Icons.Default.Clear, contentDescription = "Tyhjennä",
                            tint = if (isNightMode) Color(0xFFAAAAAA) else Color(0xFF666666))
                    }
                }
                IconButton(onClick = { speechLauncher.launch(speechIntent) }) {
                    Icon(Icons.Default.Mic, contentDescription = "Puheentunnistus",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (showSuggestions && filteredPlaces.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isNightMode) Color(0xFF2C2C2C) else Color.White
                    )
                ) {
                    Column {
                        filteredPlaces.take(5).forEachIndexed { index, place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = place.name
                                        showSuggestions = false
                                        scope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lon), 16f),
                                                durationMs = 800
                                            )
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Park, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(place.name, color = if (isNightMode) Color.White else Color.Black, fontSize = 14.sp)
                                    Text(place.typeName, color = if (isNightMode) Color(0xFF888888) else Color(0xFF999999), fontSize = 12.sp)
                                }
                            }
                            if (index < filteredPlaces.take(5).lastIndex) {
                                HorizontalDivider(color = if (isNightMode) Color(0xFF3C3C3C) else Color(0xFFEEEEEE))
                            }
                        }
                    }
                }
            }
        }

        // Profile picture button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 68.dp)
                .size(52.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
                .clickable { onNavigateToProfile() }
        ) {
            AsyncImage(model = user.imagePath ?: "https://via.placeholder.com/150",
                contentDescription = "Profiili", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape))
        }
    }
}

// --- LOG VISIT SCREEN ---
@Composable
fun LogVisitScreen(parkName: String, onSave: (Int, String, String?) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var rating by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Kirjaa käynti", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(parkName, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))

        // Camera / gallery photo picker
        PhotoPickerSheet(
            currentUri = photoUri,
            isCircle = false,
            onUriSelected = { uri ->
                if (uri != null) {
                    photoUri = uri
                    photoPath = uri.path
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Star rating
        Text("Arvosana", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            (1..5).forEach { star ->
                IconButton(onClick = { rating = star }) {
                    Icon(
                        imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarOutline,
                        contentDescription = "$star tähteä",
                        tint = if (star <= rating) Color(0xFFFFC107) else Color(0xFFBBBBBB),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Note
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Muistiinpano (valinnainen)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSave(rating, note, photoPath) },
            enabled = rating > 0,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Tallenna käynti")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Peruuta")
        }
    }
}

// --- DISPLAY SCREEN (with visits) ---
@Composable
fun DisplayScreen(
    user: UserProfile,
    visits: List<Visit>,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onDeleteVisit: (Int) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            AsyncImage(
                model = user.imagePath ?: "https://via.placeholder.com/150",
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(100.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = user.name, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Muokkaa profiilia") }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Takaisin kartalle") }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Käyntihistoria (${visits.size})",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (visits.isEmpty()) {
            item {
                Text(
                    "Ei vielä käyntejä. Napauta karttamerkintää aloittaaksesi!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(visits) { visit ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(visit.placeName, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { onDeleteVisit(visit.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Poista",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    // Stars
                    Row {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= visit.rating) Icons.Default.Star else Icons.Default.StarOutline,
                                contentDescription = null,
                                tint = if (star <= visit.rating) Color(0xFFFFC107) else Color(0xFFBBBBBB),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (visit.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(visit.note, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (visit.imagePath != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        AsyncImage(
                            model = visit.imagePath,
                            contentDescription = "Käyntikuva",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        dateFormat.format(Date(visit.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun EditScreen(onSave: (String, Uri?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PhotoPickerSheet(
            currentUri = imageUri,
            isCircle = true,
            onUriSelected = { imageUri = it }
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextField(value = name, onValueChange = { name = it }, label = { Text("Nimi") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { if (name.isNotBlank()) onSave(name, imageUri) },
            enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Tallenna")
        }
    }
}