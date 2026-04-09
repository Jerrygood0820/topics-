package com.example.taoyuantrafficgps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.taoyuantrafficgps.ui.AppNav
import com.example.taoyuantrafficgps.ui.theme.TaoyuanTrafficTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaoyuanTrafficTheme {
                AppNav()
            }
        }
    }
}
