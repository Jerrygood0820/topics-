package com.example.taoyuantrafficgps.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 客戶端單例物件
 * 負責管理所有與後端 Python 伺服器的 HTTP 連線。
 * 實作功能：[F01] 資料介接的連線基礎。
 */
object RetrofitClient {
    
    // 後端 API 的基礎位址。
    // 使用 127.0.0.1 配合 adb reverse tcp:8000 tcp:8000 指令，可讓實體手機透過 USB 存取電腦後端。
    private const val BASE_URL = "http://127.0.0.1:8000/" 

    /**
     * 自定義的 OkHttpClient
     * 用於設定超時時間以及處理特定的連線問題。
     */
    private val okHttpClient = OkHttpClient.Builder()
        // 設定連接、讀取、寫入的超時時間為 15 秒
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // 遇到連線失敗時自動重試
        .retryOnConnectionFailure(true)
        // 網路攔截器：處理 ADB 隧道常見的 "unexpected end of stream" 錯誤
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                // 強制在每個請求結束後關閉連線 (不使用 Keep-Alive)
                // 這能有效解決部分 Android 版本在 ADB 轉發下的連線中斷問題
                .addHeader("Connection", "close") 
                .build()
            chain.proceed(request)
        }
        .build()

    /**
     * 建立並回傳 API 介面實例
     * 使用延遲載入 (lazy)，僅在第一次使用時初始化。
     */
    val instance: TrafficApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            // 使用自定義的連線客戶端
            .client(okHttpClient) 
            // 使用 Gson 轉換器，自動將後端回傳的 JSON 轉為 Kotlin 資料物件 (Data Class)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrafficApiService::class.java)
    }
}
