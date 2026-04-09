package com.example.taoyuantrafficgps.data

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body

// 對應 Python 爬蟲輸出的 JSON 結構
data class SpeedRawResponse(
    val location: String,
    val speed: String, 
    val number: String
)

data class RoadworkRawResponse(
    // 移除不對應的 period，確保與 Python SQL 語法一致
    val category: String,
    val location: String,
    val description: String,
    val date: String,
    val time: String,
    val source: String
)

data class UserReportRequest(
    val type: String,
    val roadName: String,
    val lat: Double,
    val lng: Double,
    val description: String
)

interface TrafficApiService {
    @GET("api/speeds")
    suspend fun getSpeeds(): List<SpeedRawResponse>

    @GET("api/roadworks")
    suspend fun getRoadworks(): List<RoadworkRawResponse>

    @POST("api/report")
    suspend fun submitReport(@Body report: UserReportRequest)
}
