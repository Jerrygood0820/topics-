package com.example.taoyuantrafficgps.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.taoyuantrafficgps.data.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 【MapScreen】 地圖主頁面組件
 * 本功能為 App 的核心樞紐，整合 Google Maps SDK 並實作了以下關鍵模組：
 * 1. [F01] 資料介接：透過非同步協程串接後端 Python API 獲取 SQL 資料庫路況。
 * 2. [F02] 壅塞分析：動態計算車速分數並以紅/黃/綠圓圈視覺化呈現在地圖上。
 * 3. [F03] 視覺化點位：在地圖上標記道路施工、交通事故等即時事件。
 * 4. [F04] 路徑規劃：整合 OSRM (Open Source Routing Machine) 外部服務，規劃規避擁塞的路線。
 * 5. [F05] 使用者回報：支援地圖長按互動、座標反查地址以及自發性路況報修功能。
 * 6. 語音辨識搜尋：支援繁體中文目的地搜尋，優化駕駛過程中的操作便利性。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenReport: (lat: Double, lng: Double) -> Unit, // 跳轉至回報頁面的外部引導回調
    onOpenEventDetail: (eventId: String) -> Unit    // 點擊事件標記後跳轉詳情頁的回調
) {
    // --- [1] 環境與客戶端初始化 ---
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // 用於發起非同步網路請求或執行地圖動畫
    val focusManager = LocalFocusManager.current // 用於搜尋後手動收起螢幕鍵盤
    
    // 初始化 Google 定位服務客戶端，用於獲取使用者目前的精確座標
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // --- [2] 地圖相機狀態管理 ---
    // cameraPositionState 用於追蹤與手動控制地圖的視角（中心點座標與縮放倍率）。
    // 預設將地圖中心點設定在桃園市中心 (24.9936, 121.3009)，初始縮放層級為 13.5。
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(24.9936, 121.3009), 13.5f)
    }

    // --- [3] UI 響應式狀態 (State) ---
    var searchQuery by remember { mutableStateOf("") }              // 目的地搜尋輸入框文字
    var currentUserLocation by remember { mutableStateOf<LatLng?>(null) } // 裝置當前的 GPS 座標
    var navEndPoint by remember { mutableStateOf<LatLng?>(null) }      // 導航終點座標 (OSRM 請求參數)
    var navEndName by remember { mutableStateOf("") }                  // 導航終點的易讀地址名稱
    var isNavigating by remember { mutableStateOf(false) }             // 是否正在呈現路徑導航線條
    var smartPathPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) } // 儲存由 OSRM 計算回傳的軌跡點
    var clickedLocation by remember { mutableStateOf<LatLng?>(null) }  // 地圖上被選取(搜尋或長按)的點位
    var clickedAddress by remember { mutableStateOf("") }              // 該點位反查出的地址字串
    var selectedSegment by remember { mutableStateOf<RoadSegment?>(null) } // 目前選中的路段壅塞資料 (用於 BottomSheet 顯示)
    var isLoadingRoute by remember { mutableStateOf(false) }           // 資料載入中指示器的顯示狀態

    // 從 TrafficRepository 讀取即時資料流 (StateFlow)，並在生命週期內自動更新
    val roadSegments by TrafficRepository.roadSegments.collectAsStateWithLifecycle()
    val events by TrafficRepository.events.collectAsStateWithLifecycle()

    // --- [4] 語音搜尋與辨識模組 ---
    // 註冊語音辨識 Activity 的回傳處理器
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 從 Intent 中提取辨識出的文字清單，並取第一筆最準確的結果
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                searchQuery = spokenText
                // 自動觸發地址搜尋功能
                scope.launch {
                    val geocoder = Geocoder(context, Locale.TAIWAN)
                    val addrs = withContext(Dispatchers.IO) {
                        try {
                            // 在搜尋詞前補上桃園市，提高桃園本地搜尋的精準度
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocationName("桃園市$spokenText", 1)
                        } catch (e: Exception) { null }
                    }
                    addrs?.firstOrNull()?.let { addr ->
                        val p = LatLng(addr.latitude, addr.longitude)
                        // 地圖平滑飛向搜尋地點
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(p, 16f))
                        clickedAddress = spokenText
                        clickedLocation = p
                    }
                }
            }
        }
    }

    /** 啟動系統語音辨識對話框：強制鎖定為繁體中文(zh-TW) 辨識模式 */
    val startVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW") // 指定辨識語言
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-TW")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "要去桃園哪裡？") // 語音視窗上的說明
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "您的裝置不支援語音搜尋", Toast.LENGTH_SHORT).show()
        }
    }

    // --- [5] 權限處理與背景自動化任務 ---
    // 檢查精確定位權限授權狀態
    var permissionsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
    }
    // 初次載入自動請求權限
    LaunchedEffect(Unit) { if (!permissionsGranted) launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)) }

    // 設定地圖顯示屬性 (如：我的位置藍點、地圖控制鈕)
    val mapProperties = remember(permissionsGranted) {
        MapProperties(isMyLocationEnabled = permissionsGranted, isTrafficEnabled = false)
    }
    val mapUiSettings = remember {
        MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
    }

    // --- [6] 功能邏輯定義 ---

    /** [F01] 執行關鍵字地點搜尋 */
    fun performSearch(query: String) {
        if (query.isBlank()) return
        scope.launch {
            val geocoder = Geocoder(context, Locale.TAIWAN)
            val addrs = withContext(Dispatchers.IO) {
                try {
                    // 自動補上「桃園市」縮小搜尋範圍
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(if (query.contains("桃園")) query else "桃園市$query", 1)
                } catch (e: Exception) { null }
            }
            addrs?.firstOrNull()?.let { addr ->
                val p = LatLng(addr.latitude, addr.longitude)
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(p, 16f))
                clickedAddress = query
                clickedLocation = p
                focusManager.clearFocus() // 隱藏鍵盤
            } ?: run {
                Toast.makeText(context, "找不到該地點", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** [F04] 啟動規避擁塞導航：呼叫 OSRM 路徑規劃服務 */
    fun startRealNavigation(target: LatLng, name: String) {
        navEndName = name
        navEndPoint = target
        isLoadingRoute = true
        scope.launch {
            val start = currentUserLocation ?: cameraPositionState.position.target
            // 獲取規避後的座標點軌跡
            val path = TrafficRepository.fetchRealRoute(start, target)
            if (path.size > 2) {
                smartPathPoints = path
                isNavigating = true
                // 計算路線範圍並自動調整地圖視角
                val bounds = LatLngBounds.builder().apply { path.forEach { include(it) } }.build()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 150))
            } else {
                Toast.makeText(context, "導航資料獲取失敗", Toast.LENGTH_SHORT).show()
            }
            isLoadingRoute = false
        }
    }

    // [定時背景任務 A]：每 60 秒與後端 Python/SQL 同步最新路況 [F01/F02]
    LaunchedEffect(Unit) {
        TrafficRepository.fetchData(context)
        while (true) {
            delay(60000)
            TrafficRepository.fetchData(context)
        }
    }

    // [定時背景任務 B]：每 10 秒更新一次手機 GPS 座標，用於追蹤使用者目前位置
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            while (true) {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc: android.location.Location? ->
                        loc?.let { currentUserLocation = LatLng(it.latitude, it.longitude) }
                    }
                } catch (e: SecurityException) {}
                delay(10000)
            }
        }
    }

    // --- [7] UI 介面佈局實作 ---
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 【層級 1：Google Map 底圖渲染層】
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings,
            onMapClick = {
                // 點擊地圖空白處：清除選取狀態、收起鍵盤與詳情面板
                clickedLocation = null
                focusManager.clearFocus()
                selectedSegment = null
            },
            onMapLongClick = { latLng ->
                // [F05] 地圖長按點位選取與座標地址反查功能
                scope.launch {
                    val geocoder = Geocoder(context, Locale.TAIWAN)
                    val addrs = withContext(Dispatchers.IO) {
                        try {
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                        } catch (e: Exception) { null }
                    }
                    clickedAddress = addrs?.firstOrNull()?.thoroughfare ?: "標記位置"
                    clickedLocation = latLng
                }
            }
        ) {
            // A. 繪製路況壅塞圓圈點位 [F02/F03]
            roadSegments.forEach { seg ->
                // 根據路況分析分數定義顏色：紅(極擁擠)、黃(緩慢)、綠(暢通)
                val color = when {
                    seg.congestionScore > 75 -> Color(0xCCFF0000)
                    seg.congestionScore > 40 -> Color(0xCCFFD600)
                    else -> Color(0xCC00C853)
                }
                Circle(
                    center = seg.points.first(),
                    radius = 300.0,
                    fillColor = color,
                    strokeWidth = 4f,
                    strokeColor = Color.White.copy(alpha = 0.7f),
                    clickable = true,
                    onClick = {
                        // 點擊即時路況點：啟動詳情 BottomSheet
                        selectedSegment = seg
                        clickedLocation = null
                    }
                )
            }
            
            // B. 繪製交通事件標記 (如道路施工、事故) [F03]
            events.forEach { e ->
                Marker(
                    state = rememberMarkerState(position = e.position),
                    title = e.title,
                    onClick = {
                        onOpenEventDetail(e.eventId) // 呼叫引導跳轉詳情頁
                        true
                    }
                )
            }
            
            // C. 導航路線呈現 [F04]
            if (isNavigating && smartPathPoints.isNotEmpty()) {
                Polyline(
                    points = smartPathPoints,
                    color = Color(0xFF1976D2), // 經典導航藍
                    width = 20f,
                    jointType = JointType.ROUND
                )
                navEndPoint?.let {
                    Marker(state = rememberMarkerState(position = it), title = navEndName)
                }
            }
        }

        // 【層級 2：頂部互動區域 (搜尋列與導航條)】
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isNavigating) {
                // [模式 A：搜尋狀態]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.CenterHorizontally),
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("搜尋目的地") },
                        modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            // 點擊麥克風執行繁體中文語音搜尋
                            IconButton(onClick = { startVoiceSearch() }) {
                                Icon(Icons.Default.Mic, "語音搜尋", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch(searchQuery) }),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent)
                    )
                }
            } else {
                // [模式 B：導航進行中狀態]
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.CenterHorizontally),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Navigation, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("導航中：$navEndName", fontWeight = FontWeight.Bold)
                            Text("已載入即時路網數據 (自動規避壅塞)", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            // 退出導航並重設軌跡
                            isNavigating = false
                            smartPathPoints = emptyList()
                        }) {
                            Icon(Icons.Default.Close, null, tint = Color.Red)
                        }
                    }
                }
            }
        }

        // 【層級 3：右下角按鈕組 (快速定位、報修)】
        if (!isNavigating) {
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 100.dp), horizontalAlignment = Alignment.End) {
                // 定位按鈕：快速飛回使用者位置
                FloatingActionButton(
                    onClick = {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                loc?.let { pos -> scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(pos.latitude, pos.longitude), 16f)) } }
                            }
                        } catch (e: SecurityException) {
                            Toast.makeText(context, "未授權定位", Toast.LENGTH_SHORT).show()
                        }
                    },
                    containerColor = Color.White, modifier = Modifier.padding(bottom = 12.dp)
                ) { Icon(Icons.Default.MyLocation, "定位") }
                
                // 報修按鈕：開啟回報路況頁面
                FloatingActionButton(
                    onClick = { onOpenReport(currentUserLocation?.latitude ?: cameraPositionState.position.target.latitude, currentUserLocation?.longitude ?: cameraPositionState.position.target.longitude) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) { Icon(Icons.Default.Add, "報修回報") }
            }
        }

        // 【層級 4：搜尋點擊互動卡片】
        if (clickedLocation != null) {
            Card(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(), elevation = CardDefaults.cardElevation(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(clickedAddress, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(12.dp))
                    Button(onClick = { startRealNavigation(clickedLocation!!, clickedAddress); clickedLocation = null }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Directions, null); Spacer(Modifier.width(8.dp)); Text("開始導航")
                    }
                }
            }
        }

        // 【層級 5：路段詳情 BottomSheet】
        if (selectedSegment != null) {
            ModalBottomSheet(onDismissRequest = { selectedSegment = null }) {
                val s = selectedSegment!!
                Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                    Text("壅塞分析結果 (F02)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp)); Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("估算平均車速", color = Color.Gray); Text("${s.speed.toInt()} km/h", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(8.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("路況評級", color = Color.Gray)
                        val statusText = when { s.congestionScore > 75 -> "極度擁擠"; s.congestionScore > 40 -> "車多緩慢"; else -> "路況良好" }
                        Text("$statusText (${s.congestionScore}分)", color = if(s.congestionScore > 70) Color.Red else Color.Unspecified, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (isLoadingRoute) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
