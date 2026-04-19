# -*- coding: utf-8 -*-
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from webdriver_manager.chrome import ChromeDriverManager
from bs4 import BeautifulSoup
import psycopg2
import time

DB_CONFIG = {
    "dbname": "taoyuantraffic",
    "user": "postgres",
    "password": "hanky931226",
    "host": "127.0.0.1",
    "port": "5432"
}


def get_conn():
    return psycopg2.connect(**DB_CONFIG)


def get_driver():
    options = Options()
    # options.add_argument("--headless")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    return webdriver.Chrome(service=Service(ChromeDriverManager().install()), options=options)


def crawl_roadworks():
    url = "https://www.pbs.gov.tw/cht/index.php?code=list&ids=30"

    driver = get_driver()
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

        data.append({
            "period": cols[0].text.strip(),
            "category": cols[1].text.strip(),
            "location": cols[2].text.strip(),
            "description": cols[3].text.strip(),
            "event_date": cols[4].text.strip(),
            "event_time": cols[5].text.strip(),
            "source": cols[6].text.strip()
        })

    return data


def save_roadworks_to_db(data):
    conn = get_conn()
    cur = conn.cursor()

    cur.execute("TRUNCATE TABLE public.roadwork_raw RESTART IDENTITY;")

    sql = """
    INSERT INTO public.roadwork_raw
    (period, category, location, description, event_date, event_time, source)
    VALUES (%s, %s, %s, %s, %s, %s, %s)
    """

    for item in data:
        cur.execute(sql, (
            item["period"],
            item["category"],
            item["location"],
            item["description"],
            item["event_date"],
            item["event_time"],
            item["source"]
        ))

    conn.commit()
    cur.close()
    conn.close()


def crawl_speed():
    url = "https://tcc.tycg.gov.tw/ATISAccessible"

    driver = get_driver()
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

        data.append({
            "location": cols[0].text.strip(),
            "speed": cols[1].text.strip(),
            "number": cols[2].text.strip()
        })

    return data


def save_speed_to_db(data):
    conn = get_conn()
    cur = conn.cursor()

    cur.execute("TRUNCATE TABLE public.traffic_speed_raw RESTART IDENTITY;")

    sql = """
    INSERT INTO public.traffic_speed_raw
    (location, speed, number)
    VALUES (%s, %s, %s)
    """

    for item in data:
        cur.execute(sql, (
            item["location"],
            item["speed"],
            item["number"]
        ))

    conn.commit()
    cur.close()
    conn.close()


if __name__ == "__main__":
    try:
        print("開始抓施工資料...")
        roadworks = crawl_roadworks()
        print(f"施工資料共 {len(roadworks)} 筆")
        save_roadworks_to_db(roadworks)
        print("施工資料已寫入資料庫")

        print("開始抓速度資料...")
        speed_data = crawl_speed()
        print(f"速度資料共 {len(speed_data)} 筆")
        save_speed_to_db(speed_data)
        print("速度資料已寫入資料庫")

        print("全部完成")

    except Exception as e:
        print(f"發生錯誤：{e}")