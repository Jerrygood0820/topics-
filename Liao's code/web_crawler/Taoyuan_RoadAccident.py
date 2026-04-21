# -*- coding: utf-8 -*-
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from webdriver_manager.chrome import ChromeDriverManager
from bs4 import BeautifulSoup
import psycopg2
import time

DB_CONFIG = {
    "dbname": "taoyuantraffic",   # 改成你的資料庫名稱
    "user": "postgres",
    "password": "hanky931226",    # 改成你的密碼
    "host": "127.0.0.1",
    "port": "5432"
}


def get_conn():
    return psycopg2.connect(**DB_CONFIG)


def crawl_taoyuan():
    url = "https://www.pbs.gov.tw/cht/index.php?code=list&ids=30"

    options = Options()
    options.add_argument("--headless")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")

    driver = webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=options
    )
    driver.get(url)
    time.sleep(3)

    soup = BeautifulSoup(driver.page_source, "html.parser")
    driver.quit()

    rows = soup.find_all("tr", {"name": "N"})
    data = []

    for row in rows:
        cols = row.find_all("td")
        if len(cols) < 7:
            continue

        period = cols[0].text.strip()
        category = cols[1].text.strip()
        location = cols[2].text.strip()
        description = cols[3].text.strip()
        date = cols[4].text.strip()
        time_ = cols[5].text.strip()
        source = cols[6].text.strip()

        if "桃園" in location:
            data.append({
                "期間": period,
                "類別": category,
                "地點": location,
                "路況說明": description,
                "日期": date,
                "時間": time_,
                "消息來源": source
            })

    return data


def save_to_db(data):
    """將資料覆蓋寫入 roadwork_raw"""
    conn = get_conn()
    cur = conn.cursor()

    # 每次更新前先清空舊資料
    cur.execute("TRUNCATE TABLE public.roadwork_raw RESTART IDENTITY;")

    sql = """
    INSERT INTO public.roadwork_raw
    (period, category, location, description, event_date, event_time, source)
    VALUES (%s, %s, %s, %s, %s, %s, %s)
    """

    for item in data:
        cur.execute(sql, (
            item["期間"],
            item["類別"],
            item["地點"],
            item["路況說明"],
            item["日期"],
            item["時間"],
            item["消息來源"]
        ))

    conn.commit()
    cur.close()
    conn.close()

    print(f"[{time.strftime('%H:%M:%S')}] 已寫入資料庫，共 {len(data)} 筆桃園施工資料")


if __name__ == "__main__":
    print("即時施工爬蟲啟動中，每 5 秒更新一次（Ctrl + C 可停止）\n")

    while True:
        try:
            data = crawl_taoyuan()
            save_to_db(data)
            time.sleep(5)

        except KeyboardInterrupt:
            print("\n已手動停止爬蟲。")
            break

        except Exception as e:
            print(f"錯誤：{e}")
            time.sleep(10)