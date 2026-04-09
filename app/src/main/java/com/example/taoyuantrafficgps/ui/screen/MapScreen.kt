package com.example.taoyuantrafficgps.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taoyuantrafficgps.data.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenReport: (lat: Double, lng: Double) -> Unit,
    onOpenEventDetail: (eventId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val roadSegments by TrafficRepository.roadSegments.collectAsStateWithLifecycle()
    val events by TrafficRepository.events.collectAsStateWithLifecycle()
    val uiStatus by TrafficRepository.uiStatus.collectAsStateWithLifecycle()

    var currentUserLocation by remember { mutableStateOf<LatLng?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isFollowingUser by remember { mutableStateOf(false) }
    var showEventList by remember { mutableStateOf(false) }
    
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val cameraPositionState = rememberCameraPositionState {
        // 維持在桃園中心全貌
        position = CameraPosition.fromLatLngZoom(FakeRepository.taoyuanCenter, 13.5f)
    }

    @SuppressLint("MissingPermission")
    fun moveToCurrentLocation() {
        scope.launch {
            try {
                val lastLoc = fusedLocationClient.lastLocation.await()
                if (lastLoc != null) {
                    val pos = LatLng(lastLoc.latitude, lastLoc.longitude)
                    currentUserLocation = pos
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                    isFollowingUser = true
                }
                
                val freshLoc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                if (freshLoc != null) {
                    val pos = LatLng(freshLoc.latitude, freshLoc.longitude)
                    currentUserLocation = pos
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                    isFollowingUser = true
                }
            } catch (e: Exception) {
                Log.e("MapScreen", "Location Error: ${e.message}")
            }
        }
    }

    // 自動刷新資料
    LaunchedEffect(Unit) {
        while (true) {
            TrafficRepository.fetchData(context)
            delay(300_000) 
        }
    }

    // 當使用者手動操作地圖時，停止自動跟隨模式
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            isFollowingUser = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            hasLocationPermission = true
            // 只有在「剛點下授權」時才自動跳轉一次
            moveToCurrentLocation()
        }
    }

    // 定位監聽：更新座標但不強制移動鏡頭 (除非 isFollowingUser 為 true)
    DisposableEffect(hasLocationPermission) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val loc = locationResult.lastLocation ?: return
                val latLng = LatLng(loc.latitude, loc.longitude)
                currentUserLocation = latLng
                
                if (isFollowingUser) {
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng))
                    }
                }
            }
        }
        if (hasLocationPermission) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).build()
            try { fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper()) } catch (e: Exception) {}
        }
        onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
    }

    // 啟動時不再自動呼叫 moveToCurrentLocation()

    var selectedSegment by remember { mutableStateOf<RoadSegment?>(null) }
    var selectedEvent by remember { mutableStateOf<TrafficEvent?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("桃園路況即時通") },
                actions = {
                    IconButton(onClick = { showEventList = true }) { Icon(Icons.Default.List, "list") }
                    IconButton(onClick = { scope.launch { TrafficRepository.fetchData(context) } }) { Icon(Icons.Default.Refresh, "refresh") }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { if (hasLocationPermission) moveToCurrentLocation() else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) },
                    containerColor = if (isFollowingUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(bottom = 12.dp).size(48.dp)
                ) {
                    Icon(if (isFollowingUser) Icons.Default.Navigation else Icons.Default.MyLocation, "loc")
                }
                
                FloatingActionButton(onClick = { 
                    val reportLat = currentUserLocation?.latitude ?: cameraPositionState.position.target.latitude
                    val reportLng = currentUserLocation?.longitude ?: cameraPositionState.position.target.longitude
                    onOpenReport(reportLat, reportLng) 
                }) {
                    Icon(Icons.Default.Add, "report")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false)
            ) {
                roadSegments.forEach { seg ->
                    val color = when (seg.congestionScore) {
                        in 0..39 -> Color(0xBB2E7D32)
                        in 40..69 -> Color(0xBBF9A825)
                        else -> Color(0xBBC62828)
                    }
                    Circle(
                        center = seg.points.first(),
                        radius = 40.0,
                        fillColor = color,
                        strokeColor = Color.White.copy(alpha = 0.5f),
                        strokeWidth = 3f,
                        clickable = true,
                        onClick = { selectedSegment = seg }
                    )
                }

                events.forEach { e ->
                    Marker(
                        state = rememberMarkerState(position = e.position),
                        title = e.title,
                        snippet = e.locationName,
                        onClick = { selectedEvent = e; true }
                    )
                }
            }

            if (uiStatus == UiStatus.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (selectedSegment != null) {
                ModalBottomSheet(onDismissRequest = { selectedSegment = null }) {
                    val s = selectedSegment!!
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("路段：${s.name}", style = MaterialTheme.typography.titleLarge)
                        Text("即時車速：${s.speed} km/h")
                        Text(
                            "加權壅塞分數：${s.congestionScore}/100", 
                            color = if(s.congestionScore > 70) Color.Red else Color.Unspecified,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text("說明：此分數結合了即時車速與周邊 ${events.count { TrafficRepository.calculateDistance(s.points.first(), it.position) < 300 }} 件警廣/事故回報。", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { selectedSegment = null }, modifier = Modifier.fillMaxWidth()) { Text("關閉") }
                    }
                }
            }

            if (selectedEvent != null) {
                ModalBottomSheet(onDismissRequest = { selectedEvent = null }) {
                    val e = selectedEvent!!
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(e.title, style = MaterialTheme.typography.titleLarge)
                        Text("地點：${e.locationName}")
                        Text("描述：${e.description}")
                        Text("資料來源：${e.source}")
                        Button(onClick = { selectedEvent = null; onOpenEventDetail(e.eventId) }) { Text("查看詳情") }
                    }
                }
            }

            if (showEventList) {
                ModalBottomSheet(onDismissRequest = { showEventList = false }) {
                    Column(modifier = Modifier.fillMaxHeight(0.6f).padding(16.dp)) {
                        Text("即時事件列表", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn {
                            items(events) { event ->
                                ListItem(
                                    headlineContent = { Text(event.title) },
                                    supportingContent = { Text(event.locationName) },
                                    modifier = Modifier.clickable {
                                        showEventList = false
                                        scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(event.position, 15f)) }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}
