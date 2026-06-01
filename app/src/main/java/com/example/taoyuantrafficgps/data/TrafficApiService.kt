package com.example.taoyuantrafficgps.data

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body

/**
 * 桃園即時路況 API 介面定義
 * 本介面使用 Retrofit 實作，對應後端 Python FastAPI/Flask 的 API 路由。
 */

/**
 * 車速原始資料回應格式
 * 對應資料庫中的 traffic_speed_raw 處理結果
 */
data class SpeedRawResponse(
    val location: String, // 路段名稱
    val speed: String,    // 目前車速 (字串格式，需轉換)
    val number: String    // 路段編號
)

/**
 * 道路施工原始資料回應格式
 * 對應資料庫中從桃園市政府開放資料抓取的施工資訊
 */
data class RoadworkRawResponse(
    val category: String,    // 事件分類 (如: 道路工程)
    val location: String,    // 施工路段
    val description: String, // 詳細描述
    val date: String,        // 發生日期
    val time: String,        // 發生時間
    val source: String       // 來源單位 (如: 桃園市政府)
)

/**
 * 使用者路況回報請求格式
 * 用於發送 [F05] 使用者回報 功能的資料至後端資料庫
 */
data class UserReportRequest(
    val type: String,        // 事件類型 (ACCIDENT, JAM...)
    val roadName: String,    // 路段名稱
    val lat: Double,         // 經度
    val lng: Double,         // 緯度
    val description: String  // 狀況描述
)

interface TrafficApiService {
    /**
     * [F01] 抓取所有即時車速資料
     */
    @GET("api/speeds")
    suspend fun getSpeeds(): List<SpeedRawResponse>

    /**
     * [F01] 抓取目前正在進行的道路工程資訊
     */
    @GET("api/roadworks")
    suspend fun getRoadworks(): List<RoadworkRawResponse>

    /**
     * [F05] 送出使用者自發性的路況回報
     */
    @POST("api/report")
    suspend fun submitReport(@Body report: UserReportRequest)
}
