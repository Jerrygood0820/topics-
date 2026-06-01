package com.example.mappoc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class RouteInfo(
    val points: List<LatLng>,
    val distance: String = "",
    val duration: String = ""
)

enum class TravelMode(val value: String, val label: String, val emoji: String) {
    DRIVING("driving", "開車", "🚗"),
    WALKING("walking", "走路", "🚶")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                MapScreen()
            }
        }
    }
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val apiKey = "AIzaSyCmqHAItFnop8y79tu_RdmaA08zvuJOnog"
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val cameraPositionState = rememberCameraPositionState()
    var travelMode by remember { mutableStateOf(TravelMode.DRIVING) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var routeInfo by remember { mutableStateOf<RouteInfo?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val start = remember { LatLng(25.0477, 121.5170) }
    val end = remember { LatLng(25.0339, 121.5646) }

    // 自動縮放至路徑範圍的函式
    fun zoomToRoute(points: List<LatLng>) {
        if (points.isEmpty()) return
        val boundsBuilder = LatLngBounds.builder()
        points.forEach { boundsBuilder.include(it) }
        scope.launch {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150),
                durationMs = 1000
            )
        }
    }

    LaunchedEffect(start, end, travelMode) {
        isLoading = true
        val result = fetchRoute(start, end, travelMode.value, apiKey)
        isLoading = false

        if (result != null && result.points.isNotEmpty()) {
            routeInfo = result
            zoomToRoute(result.points)
        } else {
            Toast.makeText(context, "路徑抓取失敗，請確認網路或 API Key", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isTrafficEnabled = true,
                isMyLocationEnabled = hasLocationPermission,
                mapType = mapType
            ),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false)
        ) {
            Marker(
                state = MarkerState(position = start),
                title = "台北車站",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )
            Marker(
                state = MarkerState(position = end),
                title = "台北 101",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)
            )

            routeInfo?.let {
                Polyline(
                    points = it.points,
                    width = 15f,
                    color = if (travelMode == TravelMode.DRIVING) Color(0xFF4285F4) else Color(0xFF34A853),
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap()
                )
            }
        }

        // 頂部控制列
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TravelMode.entries.forEach { mode ->
                FilterChip(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    selected = travelMode == mode,
                    onClick = { travelMode = mode },
                    label = { Text("${mode.emoji} ${mode.label}") }
                )
            }
            FilterChip(
                modifier = Modifier.padding(horizontal = 4.dp),
                selected = mapType == MapType.SATELLITE,
                onClick = { mapType = if (mapType == MapType.NORMAL) MapType.SATELLITE else MapType.NORMAL },
                label = { Text(if (mapType == MapType.NORMAL) "🛰️ 衛星" else "🗺️ 一般") }
            )
        }

        // 右側懸浮按鈕組
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 160.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 回到路徑按鈕
            SmallFloatingActionButton(
                onClick = { routeInfo?.let { zoomToRoute(it.points) } },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text("🏁", fontSize = 20.sp)
            }

            // 我的位置按鈕
            FloatingActionButton(
                onClick = {
                    if (hasLocationPermission) {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                location?.let {
                                    val userLatLng = LatLng(it.latitude, it.longitude)
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(userLatLng, 15f),
                                            1000
                                        )
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            Log.e("MapPoC", "Permission denied")
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "My Location")
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        routeInfo?.let { info ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "${travelMode.emoji} ${travelMode.label}預計路線", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "距離: ${info.distance}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "時間: ${info.duration}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

suspend fun fetchRoute(start: LatLng, end: LatLng, mode: String, apiKey: String): RouteInfo? {
    return withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${start.latitude},${start.longitude}&" +
                    "destination=${end.latitude},${end.longitude}&" +
                    "mode=$mode&" +
                    "key=$apiKey"

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val jsonData = response.body?.string()

            if (jsonData != null) {
                val jsonObject = JSONObject(jsonData)
                val status = jsonObject.getString("status")
                if (status == "OK") {
                    val route = jsonObject.getJSONArray("routes").getJSONObject(0)
                    val leg = route.getJSONArray("legs").getJSONObject(0)
                    return@withContext RouteInfo(
                        points = PolyUtil.decode(route.getJSONObject("overview_polyline").getString("points")),
                        distance = leg.getJSONObject("distance").getString("text"),
                        duration = leg.getJSONObject("duration").getString("text")
                    )
                } else {
                    Log.e("MapPoC", "API Status Error: $status")
                }
            }
        } catch (e: Exception) {
            Log.e("MapPoC", "Network Error: ${e.message}")
        }
        null
    }
}
