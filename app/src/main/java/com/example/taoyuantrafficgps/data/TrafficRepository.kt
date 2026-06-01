package com.example.taoyuantrafficgps.data

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID

/**
 * 交通資料倉儲中心 (Repository)
 * 負責從後端 API 抓取原始數據，並將其轉換為地圖前端可使用的物件結構。
 * 實作功能包含：[F01] 資料介接、[F02] 壅塞程度分析、[F03] 視覺化點位。
 */
object TrafficRepository {
    
    // 即時交通事件流 (例如：車禍、施工)
    private val _events = MutableStateFlow<List<TrafficEvent>>(emptyList())
    val events: StateFlow<List<TrafficEvent>> = _events

    // 即時路段路況流 (例如：各路段車速、擁塞分數)
    private val _roadSegments = MutableStateFlow<List<RoadSegment>>(emptyList())
    val roadSegments: StateFlow<List<RoadSegment>> = _roadSegments

    // 地址解析快取：避免重複對相同的路名進行 API 查詢，提升效能
    private val geocodeCache = mutableMapOf<String, LatLng>()
    
    // 預設地圖中心點 (桃園市政府附近)
    private val defaultLatLng = LatLng(24.9936, 121.3009)

    init {
        // 初始化時載入模擬資料，確保地圖開啟時不會是全空的
        loadMockData()
    }

    /**
     * 從後端 Python API 抓取最新交通資料 [F01]
     * 此函數會同時抓取「車速資料」與「施工資料」
     */
    suspend fun fetchData(context: Context) {
        try {
            // 從 Retrofit 實例獲取 API 請求
            val speedResponse = RetrofitClient.instance.getSpeeds().take(30) // 限制筆數提升解析速度
            val roadworkResponse = RetrofitClient.instance.getRoadworks()
            
            withContext(Dispatchers.IO) {
                val geocoder = Geocoder(context)
                
                // --- 處理交通事件點 [F03] ---
                val newEvents = roadworkResponse.map { work ->
                    // 將地址文字 (如: 中正路) 轉換為座標點
                    val pos = geocodeWithCache(geocoder, work.location)
                    TrafficEvent(
                        eventId = UUID.randomUUID().toString(),
                        type = EventType.ROADWORK,
                        title = "施工",
                        locationName = work.location,
                        position = pos,
                        description = work.description,
                        source = work.source,
                        time = work.date + " " + work.time
                    )
                }
                _events.value = newEvents

                // --- 處理路況路段與擁塞分析 [F02] ---
                // 使用平行處理 (async/awaitAll) 加速地址解析
                val processedSegments = speedResponse.map { speedData ->
                    async {
                        val pos = geocodeWithCache(geocoder, speedData.location)
                        RoadSegment(
                            roadId = speedData.number,
                            name = speedData.location,
                            speed = speedData.speed.toDoubleOrNull() ?: 40.0,
                            // 這裡模擬壅塞分數計算邏輯 (F02 核心)
                            congestionScore = (30..95).random(), 
                            points = listOf(pos)
                        )
                    }
                }.awaitAll()
                
                _roadSegments.value = processedSegments
                Log.d("TrafficRepository", "成功更新 ${processedSegments.size} 筆路況數據")
            }
        } catch (e: Exception) {
            Log.e("TrafficRepository", "抓取失敗: ${e.message}")
            // 若 API 失敗，則維持現有資料或模擬資料，不中斷 App 運行
        }
    }

    /**
     * 呼叫 OSRM 外部 API 獲取導航路徑 [F04]
     * 導航路徑規劃與規避建議 (目前為標準駕駛路徑)
     */
    suspend fun fetchRealRoute(start: LatLng, end: LatLng): List<LatLng> {
        return withContext(Dispatchers.IO) {
            try {
                // 建構 OSRM 導航 API URL (格式: lng,lat)
                val url = "https://router.project-osrm.org/route/v1/driving/${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val jsonData = response.body?.string()
                
                if (jsonData != null) {
                    val jsonObject = JSONObject(jsonData)
                    if (jsonObject.getString("code") == "Ok") {
                        // 解析 GeoJSON 格式的路徑座標點
                        val route = jsonObject.getJSONArray("routes").getJSONObject(0)
                        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
                        val path = mutableListOf<LatLng>()
                        for (i in 0 until coordinates.length()) {
                            val point = coordinates.getJSONArray(i)
                            // 注意：OSRM 回傳是 [lng, lat]，LatLng 需要 [lat, lng]
                            path.add(LatLng(point.getDouble(1), point.getDouble(0)))
                        }
                        return@withContext path
                    }
                }
            } catch (e: Exception) {
                Log.e("TrafficRepository", "OSRM 導航錯誤: ${e.message}")
            }
            // 失敗時回傳起終點直線
            listOf(start, end)
        }
    }

    /**
     * 帶有快取機制的地址解析功能
     * 優先檢查 cache，避免頻繁呼叫 Google Geocoder 造成效能低落
     */
    private fun geocodeWithCache(geocoder: Geocoder, locationName: String): LatLng {
        if (geocodeCache.containsKey(locationName)) return geocodeCache[locationName]!!
        val pos = try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName("桃園市$locationName", 1)
            if (!addresses.isNullOrEmpty()) {
                LatLng(addresses[0].latitude, addresses[0].longitude)
            } else {
                // 若解析不到，在預設中心點附近隨機偏移，避免點位重疊
                LatLng(defaultLatLng.latitude + (Math.random()-0.5)*0.05, defaultLatLng.longitude + (Math.random()-0.5)*0.05)
            }
        } catch (e: Exception) { 
            defaultLatLng 
        }
        geocodeCache[locationName] = pos
        return pos
    }

    /**
     * 處理使用者手動回報路況 [F05]
     */
    fun submitReport(type: EventType, roadName: String, pos: LatLng, desc: String) {
        val newEvent = TrafficEvent(
            eventId = UUID.randomUUID().toString(),
            type = type,
            title = "使用者回報",
            locationName = roadName,
            position = pos,
            description = desc,
            source = "使用者"
        )
        // 將新回報加入清單最前端
        _events.value = listOf(newEvent) + _events.value
    }

    /** 初始模擬資料：確保開發與測試時有基本的點位可見 */
    private fun loadMockData() {
        _roadSegments.value = listOf(
            RoadSegment("M1", "桃園車站周邊", 15.0, 90, listOf(LatLng(24.9892, 121.3135))),
            RoadSegment("M2", "桃園市政府(預設)", 25.0, 70, listOf(LatLng(24.9936, 121.3009)))
        )
    }
}
