# 桃園路況即時通

Taoyuan Real-Time Traffic Information System

![Python](https://img.shields.io/badge/Python-3.10%2B-blue)
![FastAPI](https://img.shields.io/badge/FastAPI-Backend-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-blue)
![PostGIS](https://img.shields.io/badge/PostGIS-Spatial%20Database-orange)
![Android](https://img.shields.io/badge/Android-Kotlin%20%2F%20Compose-brightgreen)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

## Overview

**桃園路況即時通** 是一套以桃園地區為應用場域的即時路況分析系統，結合公部門開放資料、警廣事件資料、使用者即時回報與空間資料庫運算，提供道路壅塞狀態判斷、事件標記與行動端視覺化呈現。

本系統採用前後端分離架構，後端以 **FastAPI** 建置 RESTful API，並使用 **PostgreSQL + PostGIS** 處理道路路段、GPS 座標與事件資料之間的空間比對；前端則以 **Android Kotlin / Jetpack Compose** 搭配 **Google Maps SDK** 呈現即時路況地圖。

系統設計重點包含：

* 即時擷取桃園交通局車速資料
* 整合警廣、施工與事故事件資料
* 使用 PostGIS 進行空間比對與最近路段匹配
* 計算路段壅塞分數 `congestion_score`
* 排除紅燈停等造成的偽壅塞誤判
* 提供 Android App 地圖展示與使用者回報功能

---

## Motivation

在都市交通中，單純依靠平均車速判斷壅塞，容易受到紅燈停等、短暫車流變化或資料延遲影響。本專題希望建立一個結合多源資料的路況判斷系統，透過官方車速資料、事件資料與使用者回報互相驗證，提高即時路況判斷的準確性。

本系統聚焦於桃園地區，目標是提供比一般地圖服務更在地化、可擴充且可分析的智慧交通輔助平台。

---

## System Architecture

本系統分為五個主要層級：

```text
資料擷取層
    ↓
資料儲存與計算層
    ↓
核心邏輯層
    ↓
API 服務層
    ↓
客戶端展示層
```

### 1. Data Ingestion Layer

資料擷取層負責定期從外部資料來源取得交通資訊。

主要資料來源包含：

* 桃園交通局路段車速 API
* 警廣事件資料
* 施工與事故資料
* 使用者即時回報資料

系統透過 Crawler 定時抓取資料，並將原始資料寫入資料庫。

---

### 2. Storage & Computation Layer

資料儲存與計算層使用 **PostgreSQL + PostGIS** 作為核心資料庫。

PostGIS 可處理以下空間資料：

* 道路路段 `LineString`
* 使用者 GPS 座標 `Point`
* 事件發生位置 `Point`
* 路段與事件之間的空間距離判斷

常用空間運算包含：

* `ST_DWithin`
* `Spatial Join`
* 最近路段比對
* 使用者回報座標匹配

---

### 3. Business Logic Layer

核心邏輯層負責將原始資料轉換為可供使用者理解的路況狀態。

主要功能包含：

* 壅塞分數計算
* 紅燈誤判排除
* 多源資料驗證
* 路況狀態分類

壅塞分數公式設計如下：

```text
Score = 0.5 × SpeedScore
      + 0.2 × EventDensity
      + 0.2 × UserFeedback
      + 0.1 × Duration
```

其中：

| 參數             | 說明                  |
| -------------- | ------------------- |
| `SpeedScore`   | 目前車速相對於歷史平均車速的下降程度  |
| `EventDensity` | 路段附近是否存在事故、施工、車多等事件 |
| `UserFeedback` | 使用者回報數量與可信度         |
| `Duration`     | 低速狀態持續時間            |

---

### 4. API Service Layer

API 服務層使用 **FastAPI** 建立 RESTful API，負責將後端分析結果封裝為 JSON 格式，提供給 Android App 使用。

主要 API 功能：

* 查詢即時路況
* 查詢路段詳細資訊
* 接收使用者回報
* 回傳事件標記資料
* 回傳壅塞分數與路況等級

範例 JSON 回應：

```json
{
  "road_segment_id": 101,
  "name": "中正路－復興路口",
  "avg_speed": 18,
  "congestion_score": 82.5,
  "status_level": "congested",
  "updated_at": "2026-06-01T12:10:00"
}
```

---

### 5. Client Presentation Layer

客戶端展示層以 **Android Kotlin** 與 **Jetpack Compose** 開發，並整合 **Google Maps SDK** 顯示即時路況。

主要介面包含：

* 地圖主畫面
* 路況 Polyline 顏色顯示
* 事件 Marker 標記
* Bottom Sheet 路段資訊視窗
* 使用者回報介面
* GPS 自動定位

路況顏色設計：

| 狀態      | 條件         | 顏色 |
| ------- | ---------- | -- |
| 順暢      | > 60 km/h  | 綠色 |
| 車多 / 緩行 | 30–60 km/h | 黃色 |
| 壅塞      | < 30 km/h  | 紅色 |

---

## Features

### 即時車速資料擷取

系統會定期向桃園交通局 API 抓取即時路段車速資料，並存入資料庫以供後續分析。

### 空間資料比對

透過 PostGIS 的空間查詢功能，系統可將使用者回報或事件資料對應至最近道路路段。

### 壅塞分數演算法

系統不只依靠單一車速數值，而是整合車速、事件、使用者回報與持續時間，計算綜合壅塞分數。

### 紅燈誤判排除

為避免短暫停等紅燈被誤判為壅塞，系統加入以下條件：

```text
低速狀態持續超過 6 分鐘
且
附近存在事件資料或 2 筆以上使用者回報
才判定為壅塞
```

若條件不足，系統會將該路段維持為「緩行」或「正常停等」。

### 使用者即時回報

使用者可透過 Android App 回報：

* 事故
* 車多
* 施工
* 號誌異常

系統會自動取得 GPS 座標並寫入資料庫。

---

## UML Design

本專案依照物件導向軟體設計方法進行分析與設計，並使用 UML 表達系統架構與流程。

建議包含以下 UML 圖：

| 圖表                 | 用途                             |
| ------------------ | ------------------------------ |
| Use Case Diagram   | 描述使用者、管理者與外部資料來源的互動            |
| Class Diagram      | 描述主要類別、屬性、方法與關聯                |
| Sequence Diagram   | 描述查詢路況與使用者回報流程                 |
| Activity Diagram   | 描述資料處理與壅塞判斷流程                  |
| Deployment Diagram | 描述 Android、後端、資料庫與外部 API 的部署關係 |
| ER Diagram         | 描述資料表結構與關聯                     |

---

## Database Design

主要資料表如下：

### `road_segment`

儲存道路路段基礎資料。

| 欄位                | 型別               | 說明     |
| ----------------- | ---------------- | ------ |
| `road_segment_id` | BIGINT           | 路段 ID  |
| `name`            | TEXT             | 路段名稱   |
| `geometry`        | LineString       | 路段幾何資料 |
| `avg_speed`       | DOUBLE PRECISION | 歷史平均速度 |

---

### `traffic_speed_raw`

儲存即時車速原始資料。

| 欄位                | 型別               | 說明      |
| ----------------- | ---------------- | ------- |
| `speed_id`        | BIGINT           | 車速資料 ID |
| `road_segment_id` | BIGINT           | 對應路段    |
| `speed`           | DOUBLE PRECISION | 即時速度    |
| `timestamp`       | TIMESTAMP        | 資料時間    |

---

### `roadwork_raw`

儲存施工、事故或事件資料。

| 欄位                | 型別        | 說明           |
| ----------------- | --------- | ------------ |
| `event_id`        | BIGINT    | 事件 ID        |
| `road_segment_id` | BIGINT    | 對應路段，可為 NULL |
| `event_type`      | TEXT      | 事件類型         |
| `description`     | TEXT      | 事件描述         |
| `location`        | Point     | 事件座標         |
| `source`          | TEXT      | 資料來源         |
| `timestamp`       | TIMESTAMP | 資料時間         |

---

### `user_report`

儲存使用者回報資料。

| 欄位                | 型別        | 說明           |
| ----------------- | --------- | ------------ |
| `report_id`       | BIGINT    | 回報 ID        |
| `road_segment_id` | BIGINT    | 對應路段，可為 NULL |
| `user_id`         | BIGINT    | 使用者 ID       |
| `report_type`     | TEXT      | 回報類型         |
| `location`        | Point     | GPS 座標       |
| `timestamp`       | TIMESTAMP | 回報時間         |

---

### `congestion_status`

儲存分析後的路況狀態。

| 欄位                 | 型別        | 說明      |
| ------------------ | --------- | ------- |
| `congestion_id`    | BIGINT    | 壅塞狀態 ID |
| `road_segment_id`  | BIGINT    | 對應路段    |
| `congestion_score` | NUMERIC   | 壅塞分數    |
| `status_level`     | TEXT      | 路況狀態    |
| `updated_at`       | TIMESTAMP | 更新時間    |

---

## Technology Stack

### Backend

* Python 3.10+
* FastAPI
* Uvicorn
* PostgreSQL
* PostGIS
* Async I/O
* RESTful API

### Frontend

* Kotlin
* Android Studio
* Jetpack Compose
* Google Maps SDK
* Maps Compose
* Retrofit 2
* OkHttp
* play-services-location

### Data Processing

* Crawler
* JSON parsing
* Spatial Join
* ST_DWithin
* Congestion Score calculation
* Red-light false positive filtering

---

## Project Structure

```text
taoyuan-traffic-system/
├── backend/
│   ├── app/
│   │   ├── main.py
│   │   ├── api/
│   │   │   ├── road_status.py
│   │   │   └── user_report.py
│   │   ├── services/
│   │   │   ├── congestion_analyzer.py
│   │   │   ├── red_light_filter.py
│   │   │   └── crawler_service.py
│   │   ├── models/
│   │   │   ├── road_segment.py
│   │   │   ├── traffic_event.py
│   │   │   └── user_report.py
│   │   └── database/
│   │       ├── connection.py
│   │       └── schema.sql
│   └── requirements.txt
│
├── android/
│   ├── app/
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── java/
│   │   │       └── res/
│   │   └── build.gradle
│   └── settings.gradle
│
├── docs/
│   ├── diagrams/
│   │   ├── use_case_diagram.png
│   │   ├── class_diagram.png
│   │   ├── sequence_diagram.png
│   │   ├── activity_diagram.png
│   │   ├── deployment_diagram.png
│   │   └── er_diagram.png
│   └── report/
│
├── README.md
└── LICENSE
```

---

## Installation

### 1. Clone Repository

```bash
git clone https://github.com/YOUR_ACCOUNT/taoyuan-traffic-system.git
cd taoyuan-traffic-system
```

---

### 2. Backend Setup

```bash
cd backend
python -m venv .venv
```

Windows：

```bash
.venv\Scripts\activate
```

macOS / Linux：

```bash
source .venv/bin/activate
```

Install dependencies：

```bash
pip install -r requirements.txt
```

---

### 3. Database Setup

請先安裝 PostgreSQL 與 PostGIS，並建立資料庫：

```sql
CREATE DATABASE taoyuan_traffic;
CREATE EXTENSION postgis;
```

匯入資料表：

```bash
psql -U postgres -d taoyuan_traffic -f app/database/schema.sql
```

---

### 4. Environment Variables

建立 `.env` 檔案：

```env
DATABASE_URL=postgresql://username:password@localhost:5432/taoyuan_traffic
TAOYUAN_TRAFFIC_API_URL=https://example.com/traffic-api
POLICE_RADIO_API_URL=https://example.com/police-radio-api
```

---

### 5. Run Backend Server

```bash
uvicorn app.main:app --reload
```

預設 API 伺服器：

```text
http://127.0.0.1:8000
```

FastAPI Swagger 文件：

```text
http://127.0.0.1:8000/docs
```

---

## API Endpoints

### Get Road Status

```http
GET /road_status
```

回傳即時路況資料。

### Get Road Detail

```http
GET /road_status/{road_segment_id}
```

回傳特定路段詳細資訊。

### Submit User Report

```http
POST /user_report
```

Request body：

```json
{
  "lat": 24.9930,
  "lon": 121.3006,
  "report_type": "accident",
  "timestamp": "2026-06-01T12:10:00"
}
```

Response：

```json
{
  "success": true,
  "message": "Report submitted successfully"
}
```

---

## Android App Setup

1. 使用 Android Studio 開啟 `android/` 資料夾。
2. 在 `local.properties` 或設定檔中加入 Google Maps API Key。
3. 設定後端 API Base URL。
4. 執行 App 至 Android Emulator 或實體手機。

範例設定：

```properties
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
BASE_URL=http://10.0.2.2:8000
```

---

## Usage

### 查詢即時路況

1. 使用者開啟 Android App。
2. App 向 FastAPI 發送路況查詢請求。
3. FastAPI 呼叫 Congestion Service。
4. 系統從 PostGIS 讀取車速、事件、回報與路段資料。
5. 系統計算壅塞分數並排除紅燈誤判。
6. App 取得 JSON 後，在 Google Maps 上繪製不同顏色的路段。

---

### 使用者回報異常

1. 使用者點選「回報異常」。
2. App 自動取得 GPS 位置。
3. 使用者選擇事件類型。
4. App 送出回報資料至 FastAPI。
5. 後端寫入 `user_report` 資料表。
6. 該回報可納入後續壅塞分數計算。

---

## Congestion Detection Logic

系統判斷壅塞時，會綜合以下資料：

```text
即時車速
+ 事件資料
+ 使用者回報
+ 低速持續時間
= 壅塞分數
```

紅燈誤判排除邏輯：

```text
if speed < speed_threshold:
    if low_speed_duration > 6 minutes:
        if event_exists or user_report_count >= 2:
            status = "congested"
        else:
            status = "slow"
    else:
        status = "temporary_stop"
else:
    status = "normal"
```

---

## Screenshots

> 可將系統畫面或 UML 圖放在 `docs/diagrams/` 資料夾後更新以下連結。

### System Architecture

```md
![System Architecture](docs/diagrams/system_architecture.png)
```

### Use Case Diagram

```md
![Use Case Diagram](docs/diagrams/use_case_diagram.png)
```

### Class Diagram

```md
![Class Diagram](docs/diagrams/class_diagram.png)
```

### Android UI

```md
![Android UI](docs/diagrams/android_ui.png)
```

---

## Future Work

未來可擴充方向包含：

* 導入 Google Cloud Speech API 支援語音回報
* 建置管理端 Dashboard
* 加入 Dijkstra 演算法進行路徑最佳化
* 將壅塞分數與路徑成本結合
* 增加使用者回報可信度評分機制
* 建立歷史壅塞熱點分析
* 支援更多縣市交通資料來源

---

## Contributors

| 姓名   | 貢獻內容                      | 貢獻比例 |
| ---- | ------------------------- | ---- |
| 廖奕翰 | Crawler、資料庫設計              | 25%  |
| 李昊軒 | Android 前端、Google Maps 整合 | 25%  |
| 黃一哲 | 後端 API、資料清理              | 25%  |
| 賈子樂 | UML 文件、簡報與測試              | 25%  |

---

## License

This project is released under the MIT License.

You may use, modify, and distribute this project for academic and educational purposes.

---

## Acknowledgements

本專題感謝物件導向軟體設計課程提供 UML 設計方法與系統分析架構指導。本系統之設計概念參考智慧交通、即時資料流處理、空間資料庫與行動端地圖視覺化相關技術，並以桃園地區即時路況服務作為實作情境。
