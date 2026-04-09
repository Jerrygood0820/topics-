package com.example.taoyuantrafficgps.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.taoyuantrafficgps.data.FakeRepository
import com.example.taoyuantrafficgps.data.EventType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: String,
    onBack: () -> Unit
) {
    // 獲取最新的事件資料
    val e = FakeRepository.getEventById(eventId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("事件詳情 (F04)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (e == null) {
                Text("找不到事件資料", style = MaterialTheme.typography.titleMedium)
                Button(onClick = onBack) { Text("返回") }
                return@Column
            }

            val typeText = when (e.type) {
                EventType.ACCIDENT -> "事故"
                EventType.CONSTRUCTION -> "施工"
                EventType.JAM -> "壅塞"
                EventType.ROADWORK -> "道路工程 (警廣)"
                EventType.OTHER -> "其他"
            }

            Text(typeText, style = MaterialTheme.typography.headlineMedium)
            Text("地點：${e.locationName}", style = MaterialTheme.typography.titleMedium)
            Text("來源：${e.source}")
            if (e.time.isNotBlank()) {
                Text("發布時間：${e.time}")
            }
            
            Text("描述內容：", style = MaterialTheme.typography.titleSmall)
            Text(
                text = e.description.ifBlank { "（無詳細描述）" },
                style = MaterialTheme.typography.bodyLarge
            )

            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text("返回地圖")
            }
        }
    }
}
