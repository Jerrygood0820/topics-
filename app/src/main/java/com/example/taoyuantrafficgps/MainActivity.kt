package com.example.taoyuantrafficgps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.taoyuantrafficgps.ui.AppNav
import com.example.taoyuantrafficgps.ui.theme.TaoyuanTrafficTheme

/**
 * 【MainActivity】
 * 這是 Android 應用程式的唯一進入點。
 * 本專案採用 Single Activity 架構，配合 Jetpack Compose 進行 UI 渲染。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 設定 Compose 內容
        setContent {
            // 套用專案自定義的主題 (包含顏色、字體、形狀設定)
            TaoyuanTrafficTheme {
                // 啟動主導覽控制器，管理地圖、詳情、回報頁面的跳轉
                AppNav()
            }
        }
    }
}
