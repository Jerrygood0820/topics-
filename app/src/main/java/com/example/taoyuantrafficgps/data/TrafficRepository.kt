package com.example.taoyuantrafficgps.data

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

object TrafficRepository {
    private val _events = MutableStateFlow<List<TrafficEvent>>(emptyList())
    val events: StateFlow<List<TrafficEvent>> = _events

    private val _roadSegments = MutableStateFlow<List<RoadSegment>>(emptyList())
    val roadSegments: StateFlow<List<RoadSegment>> = _roadSegments

    private val _uiStatus = MutableStateFlow<UiStatus>(UiStatus.Ok)
    val uiStatus: StateFlow<UiStatus> = _uiStatus

    private val defaultLatLng = LatLng(24.9936, 121.3009)

    suspend fun fetchData(context: Context) {
        _uiStatus.value = UiStatus.Loading
        try {
            val speedResponse = RetrofitClient.instance.getSpeeds()
            val roadworkResponse = RetrofitClient.instance.getRoadworks()
            
            withContext(Dispatchers.IO) {
                processAndSyncData(context, speedResponse, roadworkResponse)
            }
            _uiStatus.value = UiStatus.Ok
        } catch (e: Exception) {
            Log.e("TrafficRepository", "Fetch failed: ${e.message}")
            loadMockData()
            _uiStatus.value = UiStatus.Ok 
        }
    }

    private fun loadMockData() {
        val mockEvents = listOf(
            TrafficEvent("M1", EventType.ROADWORK, "道路施工", "力行路", LatLng(24.9982, 121.3015), "API連線失敗顯示模擬資料", "系統"),
            TrafficEvent("M2", EventType.ACCIDENT, "模擬事故", "中正路", LatLng(25.0000, 121.3080), "請啟動您的 API Server", "系統")
        )
        _events.value = mockEvents
        _roadSegments.value = listOf(
            RoadSegment("R1", "力行路", 15.0, calculateWeightedScore(15.0, 50, LatLng(24.9982, 121.3015), mockEvents), listOf(LatLng(24.9982, 121.3015))),
            RoadSegment("R2", "中正路", 45.0, calculateWeightedScore(45.0, 50, LatLng(25.0000, 121.3080), mockEvents), listOf(LatLng(25.0000, 121.3080)))
        )
    }

    private fun processAndSyncData(context: Context, speeds: List<SpeedRawResponse>, works: List<RoadworkRawResponse>) {
        val geocoder = Geocoder(context)
        
        val newEvents = works.map { work ->
            val pos = geocodeAddress(geocoder, work.location)
            TrafficEvent(UUID.randomUUID().toString(), EventType.ROADWORK, work.category, work.location, pos, work.description, work.source, work.time)
        }
        _events.value = newEvents

        val newSegments = speeds.map { speedData ->
            val pos = geocodeAddress(geocoder, speedData.location)
            val s = speedData.speed.toDoubleOrNull() ?: 40.0
            
            RoadSegment(
                roadId = speedData.number,
                name = speedData.location,
                speed = s,
                congestionScore = calculateWeightedScore(s, 50, pos, newEvents),
                points = listOf(pos)
            )
        }
        _roadSegments.value = newSegments
    }

    private fun calculateWeightedScore(speed: Double, limit: Int, pos: LatLng, currentEvents: List<TrafficEvent>): Int {
        val speedScore = ((1.0 - (speed / limit)) * 100).toInt().coerceIn(0, 100)
        var finalScore = (speedScore * 0.6).toInt()

        var eventBonus = 0
        currentEvents.forEach { event ->
            val dist = calculateDistance(pos, event.position)
            if (dist < 300) {
                eventBonus += when(event.type) {
                    EventType.ACCIDENT -> 40
                    EventType.ROADWORK -> 25
                    else -> 15
                }
            }
        }
        finalScore += eventBonus.coerceAtMost(40)
        return finalScore.coerceIn(0, 100)
    }

    // 將此函式改為 public，讓 MapScreen 可以存取
    fun calculateDistance(p1: LatLng, p2: LatLng): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0].toDouble()
    }

    private fun geocodeAddress(geocoder: Geocoder, locationName: String): LatLng {
        return try {
            val query = if (locationName.contains("桃園")) locationName else "桃園市$locationName"
            val addresses = geocoder.getFromLocationName(query, 1)
            if (!addresses.isNullOrEmpty()) LatLng(addresses[0].latitude, addresses[0].longitude)
            else LatLng(defaultLatLng.latitude + (Math.random()-0.5)*0.02, defaultLatLng.longitude + (Math.random()-0.5)*0.02)
        } catch (e: Exception) { defaultLatLng }
    }
}
