package com.example.mappoc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil // 來自 android-maps-utils
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MapScreen()
        }
    }
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val apiKey = "AIzaSyCmqHAItFnop8y79tu_RdmaA08zvuJOnog" // <--- 填入你的 API KEY

    // 狀態管理
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPositionState = rememberCameraPositionState()

    // 儲存「沿路」的路徑點
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }

    // 設定起點與終點
    val start = remember { LatLng(25.0478, 121.5319) }
    val end = remember { LatLng(25.0330, 121.5654) }
    // 台北101

    // 權限請求器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    // 啟動時檢查權限
    LaunchedEffect(start, end) {
        val result = fetchRoute(start, end, apiKey)
        if (result.isNotEmpty()) {
            routePoints = result
            cameraPositionState.position = CameraPosition.fromLatLngZoom(start, 14f)
        }
    }

    // 重點：當程式啟動時，去抓取「沿路導航」的點
    LaunchedEffect(start, end) {
        routePoints = fetchRoute(start, end, apiKey)
        // 抓到後移動鏡頭看整條線
        cameraPositionState.position = CameraPosition.fromLatLngZoom(start, 14f)
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isTrafficEnabled = true,
            isMyLocationEnabled = hasLocationPermission
        )
    ) {
        Marker(state = MarkerState(position = start), title = "起點")
        Marker(state = MarkerState(position = end), title = "終點")

        // 畫出「沿路」的藍色導航線
        if (routePoints.isNotEmpty()) {
            Polyline(
                points = routePoints,
                width = 15f,
                color = Color(0xFF4285F4), // Google 經典導航藍
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap()
            )
        }
    }
}

/**
 * 這就是「第二步」與「第四步」的實作：
 * 1. 向 Google 發送網路請求 (OkHttp)
 * 2. 拿到 JSON 後解析出 Encoded Polyline
 * 3. 使用 PolyUtil 解碼成經緯度清單
 */
suspend fun fetchRoute(start: LatLng, end: LatLng, apiKey: String): List<LatLng> {
    return withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=${start.latitude},${start.longitude}&" +
                    "destination=${end.latitude},${end.longitude}&" +
                    "mode=driving&" + // 設為開車模式
                    "key=$apiKey"

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val jsonData = response.body?.string()

            if (jsonData != null) {
                val jsonObject = JSONObject(jsonData)
                val routes = jsonObject.getJSONArray("routes")
                if (routes.length() > 0) {
                    // 這是 Google 回傳的加密路徑字串
                    val points = routes.getJSONObject(0)
                        .getJSONObject("overview_polyline")
                        .getString("points")

                    // 使用工具解碼回 List<LatLng>
                    return@withContext PolyUtil.decode(points)
                }
            }
        } catch (e: Exception) {
            Log.e("MapPoC", "抓取路徑失敗: ${e.message}")
        }
        emptyList()
    }
}