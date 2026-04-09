package com.example.taoyuantrafficgps.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 這裡填入您的 API 伺服器網址
    // 如果是用 Android 模擬器測試本地電腦的 API，請使用 http://10.0.2.2:8000/
    private const val BASE_URL = "http://10.0.2.2:8000/" 

    val instance: TrafficApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrafficApiService::class.java)
    }
}
