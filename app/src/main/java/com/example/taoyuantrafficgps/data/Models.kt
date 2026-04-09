package com.example.taoyuantrafficgps.data

import com.google.android.gms.maps.model.LatLng

// 對應 SQL: roadwork_raw & traffic_event
enum class EventType { ACCIDENT, CONSTRUCTION, JAM, OTHER, ROADWORK }

data class TrafficEvent(
    val eventId: String,
    val type: EventType,
    val title: String,
    val locationName: String, // 對應 SQL 的 location 欄位
    val position: LatLng,
    val description: String,
    val source: String = "警廣",
    val time: String = ""
)

// 對應 SQL: traffic_speed_raw
data class RoadSpeedInfo(
    val location: String,
    val speed: Double,
    val number: String,
    val congestionScore: Int // 根據速度與速限換算的 F02 功能
)

data class RoadSegment(
    val roadId: String,
    val name: String,
    val speed: Double,
    val congestionScore: Int,
    val points: List<LatLng>
)

sealed class UiStatus {
    data object Ok : UiStatus()
    data object Loading : UiStatus()
    data object NeedLocationPermission : UiStatus()
}
