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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Search
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import java.util.Locale

// --- REITIT ---
@Serializable object SetupRoute
@Serializable object MapRoute
@Serializable object DisplayRoute
@Serializable object EditRoute

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(applicationContext)
        val db = AppDatabase.getDatabase(applicationContext)
        val userDao = db.userDao()

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
                                onNavigateToProfile = { navController.navigate(DisplayRoute) }
                            )
                        }

                        composable<DisplayRoute> {
                            val userState = remember {
                                mutableStateOf(userDao.getUser() ?: UserProfile(name = "", imagePath = null))
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
                                navController.popBackStack()
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

@Composable
fun SetupScreen(onComplete: (String, Uri?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { imageUri = it }

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

        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(model = imageUri, contentDescription = "Profiilikuva",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📷", style = MaterialTheme.typography.headlineMedium)
                    Text("Lisää kuva", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

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

@Composable
fun MapScreen(user: UserProfile, onNavigateToProfile: () -> Unit) {
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
    var prevNightMode by remember { mutableStateOf<Boolean?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }

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
        if (prevNightMode != null && prevNightMode != isNightMode) {
            sendModeNotification(context, isNightMode)
        }
        prevNightMode = isNightMode
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
                        title = place.name, snippet = place.typeName,
                        onClick = { it.showInfoWindow(); true }
                    )
                }
            }
        }

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
                    Icon(Icons.Default.KeyboardVoice, contentDescription = "Puheentunnistus",
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
                                Icon(Icons.Default.Nature, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(place.name,
                                        color = if (isNightMode) Color.White else Color.Black,
                                        fontSize = 14.sp)
                                    Text(place.typeName,
                                        color = if (isNightMode) Color(0xFF888888) else Color(0xFF999999),
                                        fontSize = 12.sp)
                                }
                            }
                            if (index < filteredPlaces.take(5).lastIndex) {
                                HorizontalDivider(
                                    color = if (isNightMode) Color(0xFF3C3C3C) else Color(0xFFEEEEEE)
                                )
                            }
                        }
                    }
                }
            }
        }

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

@Composable
fun DisplayScreen(user: UserProfile, onEdit: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AsyncImage(model = user.imagePath ?: "https://via.placeholder.com/150",
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(120.dp).clip(CircleShape))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = user.name, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Muokkaa profiilia") }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Takaisin kartalle") }
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
        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                AsyncImage(model = imageUri, contentDescription = "Profiilikuva",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Text("📷", style = MaterialTheme.typography.headlineMedium)
            }
        }
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