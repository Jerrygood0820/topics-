package com.example.taoyuantrafficgps.ui.screen

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.taoyuantrafficgps.data.EventType
import com.example.taoyuantrafficgps.data.FakeRepository
import com.google.android.gms.maps.model.LatLng
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    initialLat: Double?,
    initialLng: Double?,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val defaultPos = LatLng(initialLat ?: 24.9936, initialLng ?: 121.3009)

    var selectedType by remember { mutableStateOf(EventType.ACCIDENT) }
    var roadName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    // F05: 語音回報邏輯實作
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
    }

    DisposableEffect(Unit) {
        val listener = object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                description = data?.get(0) ?: description
                isListening = false
            }
            override fun onReadyForSpeech(p0: Bundle?) { isListening = true }
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(p0: Int) { isListening = false }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose { speechRecognizer.destroy() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("語音即時回報 (F05)") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.ArrowBack, "back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("事件類型", style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EventType.values().filter { it != EventType.ROADWORK }.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(when(type){
                            EventType.ACCIDENT -> "事故"
                            EventType.JAM -> "壅塞"
                            else -> "其他"
                        }) }
                    )
                }
            }

            OutlinedTextField(
                value = roadName,
                onValueChange = { roadName = it },
                label = { Text("事發路段") },
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("狀況簡述 (可使用語音)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                // 語音按鈕
                FilledIconButton(
                    onClick = { speechRecognizer.startListening(speechIntent) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "speech")
                }
            }

            if (isListening) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("正在聆聽中...請說出路況描述", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    FakeRepository.addReport(selectedType, roadName, defaultPos, description)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("送出回報 (F05)")
            }
        }
    }
}
