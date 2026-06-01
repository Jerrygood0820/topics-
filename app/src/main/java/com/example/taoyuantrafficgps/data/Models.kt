package com.example.taoyuantrafficgps.data

import com.google.android.gms.maps.model.LatLng

/**
 * 交通事件類型枚舉
 * 對應資料庫中的事件分類
 */
enum class EventType { 
    ACCIDENT,     // 交通事故
    CONSTRUCTION, // 一般施工
    JAM,          // 交通擁塞
    OTHER,        // 其他雜項
    ROADWORK      // 道路工程 (SQL: roadwork_raw)
}

/**
 * 交通事件完整資料物件
 * 用於在地圖上顯示標記 (Marker) 及詳情頁面
 */
data class TrafficEvent(
    val eventId: String,      // 唯一識別碼
    val type: EventType,      // 事件分類
    val title: String,        // 標題 (例如：道路施工)
    val locationName: String, // 地點名稱 (例如：桃園市中正路)
    val position: LatLng,     // 經緯度座標 (用於地圖渲染)
    val description: String,  // 詳細內容描述
    val source: String = "警廣", // 來源 (例如：警廣、桃園市政府、使用者)
    val time: String = ""     // 發生或更新時間
)

/**
 * 道路車速資訊物件
 * 對應 SQL: traffic_speed_raw 處理後的結果
 */
data class RoadSpeedInfo(
    val location: String,     // 路段名稱
    val speed: Double,         // 平均車速
    val number: String,        // 路段編號
    val congestionScore: Int  // [F02] 壅塞程度分析分數 (0-100)
)

/**
 * 地圖顯示用的路段物件
 * 整合座標與路況資訊，用於在地圖上繪製圓圈或線段
 */
data class RoadSegment(
    val roadId: String,       // 道路 ID
    val name: String,         // 道路名稱
    val speed: Double,         // 該路段目前車速
    val congestionScore: Int, // 壅塞分數
    val points: List<LatLng>  // 路段包含的座標點軌跡
)

/**
 * UI 狀態封裝類別
 * 用於管理頁面載入、成功或權限缺失等不同階段的顯示
 */
sealed class UiStatus {
    data object Ok : UiStatus()                    // 狀態正常
    data object Loading : UiStatus()               // 資料載入中
    data object NeedLocationPermission : UiStatus() // 缺少定位權限
}
