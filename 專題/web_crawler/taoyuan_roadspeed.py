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
    """爬取桃園市交通即時路況平台的資料"""
    url = "https://tcc.tycg.gov.tw/ATISAccessible"

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

    rows = soup.find_all("tr")
    data = []

    for row in rows:
        cols = row.find_all("td")
        if len(cols) < 3:
            continue

        location = cols[0].text.strip()
        speed = cols[1].text.strip().replace("速率", "").replace(":", "").replace("km", "").strip()
        number = cols[2].text.strip()

        data.append({
            "地點": location,
            "速度": speed,
            "編號": number
        })

    return data


def save_to_db(data):
    """將資料覆蓋寫入 traffic_speed_raw"""
    conn = get_conn()
    cur = conn.cursor()

    # 每次更新前先清空舊資料
    cur.execute("TRUNCATE TABLE public.traffic_speed_raw RESTART IDENTITY;")

    sql = """
    INSERT INTO public.traffic_speed_raw
    (location, speed, number)
    VALUES (%s, %s, %s)
    """

    for item in data:
        cur.execute(sql, (
            item["地點"],
            item["速度"],
            item["編號"]
        ))

    conn.commit()
    cur.close()
    conn.close()

    print(f"[{time.strftime('%H:%M:%S')}] 已寫入資料庫，共 {len(data)} 筆")


if __name__ == "__main__":
    print("桃園交通即時爬蟲啟動中，每 5 秒更新一次（Ctrl + C 可停止）\n")

    while True:
        try:
            data = crawl_taoyuan()
            save_to_db(data)
            time.sleep(5)

        except KeyboardInterrupt:
            print("\n已手動停止爬蟲。")
            break

        except Exception as e:
            print(f"[{time.strftime('%H:%M:%S')}] 發生錯誤：{e}")
            time.sleep(10)
