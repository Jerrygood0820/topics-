package com.example.taoyuantrafficgps.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.taoyuantrafficgps.ui.screen.EventDetailScreen
import com.example.taoyuantrafficgps.ui.screen.MapScreen
import com.example.taoyuantrafficgps.ui.screen.ReportScreen

/**
 * 【路由路徑定義】
 * 使用物件管理字串常數，避免在導覽過程中出現拼字錯誤。
 */
object Routes {
    const val MAP = "map"               // 地圖主螢幕
    const val EVENT_DETAIL = "eventDetail" // 交通事件詳情
    const val REPORT = "report"         // 路況回報頁面
}

/**
 * 【應用程式導覽中心】
 * 使用 Jetpack Compose Navigation 實作 Single Activity 的頁面切換架構。
 */
@Composable
fun AppNav() {
    // 建立導覽控制器實例
    val nav = rememberNavController()

    // 定義 NavHost，設定初始畫面為「地圖」
    NavHost(navController = nav, startDestination = Routes.MAP) {
        
        // --- A. 地圖首頁 ---
        composable(Routes.MAP) {
            MapScreen(
                // 當點擊報修按鈕時，帶入當前點選的經緯度參數
                onOpenReport = { lat, lng ->
                    nav.navigate("${Routes.REPORT}?lat=$lat&lng=$lng")
                },
                // 當點擊地圖標記時，傳遞事件 ID 跳轉至詳情頁
                onOpenEventDetail = { eventId ->
                    nav.navigate("${Routes.EVENT_DETAIL}/$eventId")
                }
            )
        }

        // --- B. 事件詳情頁面 ---
        // 使用路徑參數 {eventId} 傳遞資料
        composable(
            route = "${Routes.EVENT_DETAIL}/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStack ->
            val eventId = backStack.arguments?.getString("eventId").orEmpty()
            EventDetailScreen(
                eventId = eventId, 
                onBack = { nav.popBackStack() } // 返回地圖
            )
        }

        // --- C. 路況回報頁面 ---
        // 使用查詢參數 (?lat={lat}&lng={lng}) 傳遞座標，設定預設值為空字串以防解析失敗
        composable(
            route = "${Routes.REPORT}?lat={lat}&lng={lng}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType; defaultValue = "" },
                navArgument("lng") { type = NavType.StringType; defaultValue = "" },
            )
        ) { backStack ->
            val latStr = backStack.arguments?.getString("lat").orEmpty()
            val lngStr = backStack.arguments?.getString("lng").orEmpty()
            ReportScreen(
                // 將字串轉回 Double，供 Geocoder 使用
                initialLat = latStr.toDoubleOrNull(),
                initialLng = lngStr.toDoubleOrNull(),
                onDone = { nav.popBackStack() }, // 回報完成後返回
            )
        }
    }
}
