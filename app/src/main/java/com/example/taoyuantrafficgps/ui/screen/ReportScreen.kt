package com.example.taoyuantrafficgps.ui.screen

import android.app.Activity
import android.content.Intent
import android.location.Geocoder
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taoyuantrafficgps.data.EventType
import com.example.taoyuantrafficgps.data.TrafficRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 路況回報螢幕組件
 * 提供使用者選擇事件類型、確認路段名稱並輸入詳細狀況（支援語音輸入）。
 *
 * @param initialLat 初始緯度（通常為地圖點擊的位置或當前位置）
 * @param initialLng 初始經度
 * @param onDone 回報完成或點擊返回後的回調
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    initialLat: Double?,
    initialLng: Double?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // --- 狀態管理 ---
    // 保存回報的地點座標
    val livePos by remember { mutableStateOf(LatLng(initialLat ?: 24.9936, initialLng ?: 121.3009)) }
    // 目前選擇的事件類型（預設為事故）
    var selectedType by remember { mutableStateOf(EventType.ACCIDENT) }
    // 路段名稱（會透過 Geocoder 自動解析）
    var roadName by remember { mutableStateOf("正在定位路段...") }
    // 詳細狀況描述文字
    var description by remember { mutableStateOf("") }

    // --- 語音辨識處理邏輯 ---
    // 註冊語音辨識 Activity 的回傳處理器
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 獲取辨識出的文字清單，取第一個最準確的結果
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (spokenText != null) {
                // 將辨識結果串接在現有文字後面
                description = if (description.isBlank()) spokenText else "$description $spokenText"
            }
        }
    }

    /**
     * 啟動語音輸入：
     * 強制設定語言為繁體中文(台灣)，並開啟語音輸入視窗。
     */
    val startVoiceInput = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-TW")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "請說出路況描述...")
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "您的裝置不支援語音輸入", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 地理編碼任務 ---
    // 當座標 livePos 改變時，自動啟動後台任務將座標轉為路名
    LaunchedEffect(livePos) {
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.TAIWAN)
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(livePos.latitude, livePos.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    // 取出路名或地標名稱
                    roadName = (addr.thoroughfare ?: addr.featureName ?: "未知路段").trim()
                }
            } catch (e: Exception) { roadName = "未知路段" }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("回報路況", fontWeight = FontWeight.Bold) },
                navigationIcon = { 
                    IconButton(onClick = onDone) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") 
                    } 
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // 步驟 1：選擇類型
            Text("1. 選擇事件類型", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(EventType.ACCIDENT, EventType.JAM, EventType.CONSTRUCTION).forEach { type ->
                    val label = when(type) { 
                        EventType.ACCIDENT -> "事故" 
                        EventType.JAM -> "塞車" 
                        else -> "施工" 
                    }
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(label) }
                    )
                }
            }

            // 步驟 2：確認地點（可手動修改解析出的路名）
            Text("2. 確認地點", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = roadName, onValueChange = { roadName = it },
                label = { Text("回報路段") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color.Red) },
                shape = RoundedCornerShape(12.dp)
            )

            // 步驟 3：狀況描述文字與語音按鈕
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("3. 狀況描述", style = MaterialTheme.typography.titleMedium)
                // 點擊此按鈕即可說話
                TextButton(onClick = { startVoiceInput() }) {
                    Icon(Icons.Default.Mic, null)
                    Spacer(Modifier.width(4.dp))
                    Text("語音輸入")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("請說出或輸入狀況描述...") },
                    modifier = Modifier.fillMaxSize(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            // 提交按鈕
            Button(
                onClick = {
                    scope.launch {
                        // 呼叫 Repository 提交資料至後端
                        TrafficRepository.submitReport(selectedType, roadName, livePos, description)
                        snackbarHostState.showSnackbar("回報完成，感謝您的熱心回報！")
                        delay(1000)
                        onDone()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = description.isNotBlank() // 描述不為空才允許點擊
            ) {
                Text("確認送出回報", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
