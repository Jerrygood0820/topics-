桃園交通 GPS：MapScreen 核心程式技術實作細節
1. 響應式 UI 與狀態生命週期管理 (State & Lifecycle)
MapScreen 採用 Jetpack Compose 的宣告式架構，將 UI 與底層數據流深度綁定：
權限響應機制：透過 rememberLauncherForActivityResult 實作 Runtime Permissions 請求。mapProperties 使用 remember(permissionsGranted)，當定位授權狀態變更時，地圖層會自動切換 isMyLocationEnabled 狀態，確保系統安全性與效能平衡。
非同步數據輪詢 (Polling)：在 LaunchedEffect(Unit) 中實作協程機制，以 while(true) 循環每 60 秒調用 TrafficRepository.fetchData。這展現了增量更新 (Incremental Update) 的設計思想，確保路網數據（路段壅塞率與事件）始終保持時效性。
設備追蹤採樣：透過 fusedLocationClient 每 10 秒進行一次座標採樣。此處採用 addOnSuccessListener 進行非阻塞回呼，並將結果存入 currentUserLocation，作為後續 AI 感知器驗證模型（速度校驗）的核心輸入。
2. GIS 地理資訊處理與異步調度 (GIS & Concurrency)
系統在處理地理數據時，嚴格遵守 Android 非同步開發規範：
IO 調度優化：在 performSearch（搜尋目的地）與 onMapLongClick（長按標記）邏輯中，強制使用 withContext(Dispatchers.IO) 執行 Geocoder 運算。這防止了高耗時的地理編碼解析阻塞 Main Thread，保證地圖在進行 UI 渲染時不會出現任何卡頓或影格掉落 (Dropped Frames)。
空間邊界計算 (Bounding Box)：在導航啟動時，系統獲取 OSRM 路由路徑後，利用 LatLngBounds.builder() 動態計算包含起點與終點的最小外接矩形。透過 cameraPositionState.animate 實現平滑的視角轉換，自動調整縮放權重 (Padding=150)，提供優良的導航視覺體驗。
3. 智慧路網渲染與視覺化邏輯 (Visualization Logic)
路況數據的呈現並非單純畫圖，而是基於後端演算法的視覺化映射：
路段壅塞度映射：程式碼中透過 roadSegments.forEach 遍歷實時路網。核心邏輯如下：
當 congestionScore > 75：映射為 紅色 (0xCCFF0000)，代表嚴重壅塞。
當 congestionScore > 40：映射為 黃色 (0xCCFFD600)，代表車多繁忙。
其餘狀況：映射為 綠色 (0xCC00C853)，代表流速正常。
空間標記管理：利用 Marker 與 Circle 組件的分層渲染，實現「路段壓力」與「突發事件」的雙重呈現。每個標記皆具備 eventId 索引，點擊後觸發 onOpenEventDetail 進入詳情模組，完成數據的深層互動。
4. 導航路由與數據解碼 (Routing & Polyline)
路由引擎封裝：startRealNavigation 整合了後端路由服務。它不僅傳送起點與終點，更調用了 TrafficRepository.fetchRealRoute。
專業 Polyline 處理：系統接收來自 OSRM 引擎的數據後，透過自主解析將壓縮字串轉化為 smartPathPoints 座標清單。渲染時使用 Polyline 組件，並設定 JointType.ROUND 以優化轉彎處的幾何平滑度，寬度設定為 20f 以確保行車時的易視性。
5. 使用者體驗與情境感知識別 (UX & Context Awareness)
多模態輸入整合：搜尋列整合了 startVoiceSearch 觸發器，實現 語音驅動介面 (Voice-UI)。
情境切換邏輯：系統透過 isNavigating 狀態布林值切換頂部介面。在導航模式下，UI 會自動由「搜尋模式」切換為「即時指引模式」，並顯示「已載入即時路網數據」提示，增強使用者對 AI 驗證結果的信任感。
Bottom UI 彈性設計：整合 ModalBottomSheet 呈現壅塞分析結果，讓使用者能深入查看 congestionScore 背後的詳細資訊（如流速、事故關聯），體現了透明化決策 (Transparent AI) 的設計理念。

使用者操作說明 (User Manual)
本系統旨在提供駕駛者最即時且經 AI 驗證的交通資訊，操作流程如下：
1. 初始啟動與權限授權
權限要求：首次啟動時，App 會要求「精確位置 (GPS)」與「錄音 (語音辨識)」權限。
我的位置：點擊右下角白色懸浮按鈕（MyLocation），地圖將自動平滑移動並聚焦至目前位置。
2. 即時路況瀏覽
視覺化路網：地圖上會呈現紅、黃、綠三種顏色的圓點或路段：
🔴 紅色：嚴重壅塞，平均時速極低。
🟡 黃色：交通繁忙，行車緩慢。
🟢 綠色：一路順暢。
事件點查詢：點擊地圖上的藍色標記（Marker），可查看特定路段的事故、施工或活動詳情。
3. 智慧搜尋與語音操控
文字搜尋：在頂部搜尋列輸入地點（如：桃園火車站），系統自動進行地理編碼定位。
語音搜尋：點擊搜尋列右側的麥克風圖示，說出目的地，系統將自動解析並移動地圖。
4. 多模態路況回報 (AI 驗證機制)
回報方式：長按地圖任意點，或點擊右下角加號按鈕。
語音描述：進入回報頁面後，長按錄音鈕描述路況。
AI 審核：系統會自動擷取當前車速。若您說「塞車」但時速高於 50km，系統將顯示「數據異常」並攔截誤報；若通過驗證，地圖將立即更新路況。
5. 智慧導航
路徑規劃：選定目的地後點擊「開始導航」，系統會調用後端 OSRM 引擎，避開高壅塞分數路段，並在畫面上渲染藍色導航線（Polyline）。
