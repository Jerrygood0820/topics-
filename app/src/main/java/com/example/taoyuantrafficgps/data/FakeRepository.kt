package com.example.taoyuantrafficgps.data

import com.google.android.gms.maps.model.LatLng
import java.util.UUID

object FakeRepository {

    val taoyuanCenter = LatLng(24.9936, 121.3009)

    private val roadSegments = mutableListOf(
        RoadSegment(
            roadId = "S001",
            name = "力行路(桃園區)",
            speed = 18.0,
            congestionScore = 70,
            points = listOf(LatLng(24.9982, 121.3015), LatLng(25.0005, 121.2985))
        ),
        RoadSegment(
            roadId = "S002",
            name = "三民路一段",
            speed = 18.0,
            congestionScore = 70,
            points = listOf(LatLng(24.9965, 121.3155), LatLng(25.0002, 121.3188))
        )
    )

    private val events = mutableListOf(
        TrafficEvent(
            eventId = "RW001",
            type = EventType.ROADWORK,
            title = "道路施工",
            locationName = "龍潭區員樹林",
            position = LatLng(24.8955, 121.2455),
            description = "路面改善工程",
            source = "公路局"
        )
    )

    fun loadRoadSegments(): List<RoadSegment> = roadSegments

    fun loadEvents(): List<TrafficEvent> = events

    fun getEventById(eventId: String): TrafficEvent? = events.firstOrNull { it.eventId == eventId }

    fun addReport(
        type: EventType,
        roadName: String,
        position: LatLng,
        description: String
    ): TrafficEvent {
        val e = TrafficEvent(
            eventId = UUID.randomUUID().toString(),
            type = type,
            title = "使用者回報",
            locationName = roadName,
            position = position,
            description = description,
            source = "User"
        )
        events.add(0, e)
        return e
    }
}
