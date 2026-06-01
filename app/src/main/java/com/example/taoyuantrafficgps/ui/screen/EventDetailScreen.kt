package com.example.taoyuantrafficgps.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.taoyuantrafficgps.data.TrafficRepository
import com.example.taoyuantrafficgps.data.EventType

/**
 * 交通事件詳情螢幕 [F03/F04 相關]
 * 當使用者在地圖上點擊特定的交通標記 (Marker) 時，會跳轉至此頁面。
 * 
 * @param eventId 傳入的事件唯一識別碼，用於從 Repository 檢索資料。
 * @param onBack 點擊返回按鈕時的處理邏輯。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: String,
    onBack: () -> Unit
) {
    // 從實時資料流中獲取所有事件，並找出匹配 eventId 的那一筆
    val allEvents by TrafficRepository.events.collectAsState()
    val e = allEvents.find { it.eventId == eventId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("事件詳情內容") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // 使用自動轉向的返回圖示 (相容於從左往右與從右往左語言)
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 安全檢查：若找不到事件（例如資料剛好更新），顯示提示並提供返回鈕
            if (e == null) {
                Text("找不到該事件資料", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBack) { Text("回地圖") }
                return@Column
            }

            // --- A. 事件類別顯示 ---
            // 將枚舉類型轉換為中文描述
            val typeText = when (e.type) {
                EventType.ACCIDENT -> "交通事故"
                EventType.CONSTRUCTION -> "一般施工"
                EventType.JAM -> "交通擁塞"
                EventType.ROADWORK -> "道路工程 (開放資料)"
                EventType.OTHER -> "其他事件"
            }

            Text(typeText, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            
            // --- B. 地點與來源資訊 ---
            Text("回報地點：${e.locationName}", style = MaterialTheme.typography.titleMedium)
            Text("資料來源：${e.source}", style = MaterialTheme.typography.bodyMedium)
            
            // 如果有時間戳記，則顯示發布時間
            if (e.time.isNotBlank()) {
                Text("發布時間：${e.time}", style = MaterialTheme.typography.bodySmall)
            }
            
            // --- C. 詳細狀況描述 [F01 介接內容] ---
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("狀況詳細描述：", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                text = e.description.ifBlank { "（暫無詳細描述內容）" },
                style = MaterialTheme.typography.bodyLarge
            )

            // 底部操作按鈕
            Button(
                onClick = onBack, 
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text("我知道了，返回地圖")
            }
        }
    }
}
