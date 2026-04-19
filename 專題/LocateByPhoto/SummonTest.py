import os
from PIL import Image
import piexif
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from webdriver_manager.chrome import ChromeDriverManager
import time
import sys

sys.stdout.reconfigure(encoding='utf-8')


# ============================================
# 1. 生成含 GPS EXIF 的 JPEG
# ============================================

def create_gps_jpeg(path, lat=25.0416667, lon=121.5041667):
    img = Image.new("RGB", (800, 600), (30, 180, 90))
    img.save(path, "jpeg")

    def deg_to_dms_rational(deg):
        d = int(deg)
        m = int((deg - d) * 60)
        s = round(((deg - d) * 60 - m) * 60 * 100)
        return ((d, 1), (m, 1), (s, 100))

    gps_ifd = {
        piexif.GPSIFD.GPSLatitudeRef: 'N' if lat >= 0 else 'S',
        piexif.GPSIFD.GPSLatitude: deg_to_dms_rational(abs(lat)),
        piexif.GPSIFD.GPSLongitudeRef: 'E' if lon >= 0 else 'W',
        piexif.GPSIFD.GPSLongitude: deg_to_dms_rational(abs(lon)),
    }

    exif_dict = {"GPS": gps_ifd}
    piexif.insert(piexif.dump(exif_dict), path)

    print(f"已建立含 GPS 的 JPEG：{path}")


# ============================================
# 2. 讀取 EXIF + 解析經緯度
# ============================================

def rational_to_float(r):
    return r[0] / r[1]

def dms_to_dd(dms, ref):
    d = rational_to_float(dms[0])
    m = rational_to_float(dms[1])
    s = rational_to_float(dms[2])
    dd = d + m / 60 + s / 3600
    if ref in ['S', 'W']:
        dd = -dd
    return dd

def read_gps_from_image(path):
    exif_dict = piexif.load(path)

    gps = exif_dict["GPS"]
    lat_ref = gps[piexif.GPSIFD.GPSLatitudeRef].decode()
    lon_ref = gps[piexif.GPSIFD.GPSLongitudeRef].decode()
    lat_dms = gps[piexif.GPSIFD.GPSLatitude]
    lon_dms = gps[piexif.GPSIFD.GPSLongitude]

    lat = dms_to_dd(lat_dms, lat_ref)
    lon = dms_to_dd(lon_dms, lon_ref)
    return lat, lon


# ============================================
# 3. Selenium 全自動（無需 chromedriver.exe）
# ============================================

def get_address_from_google(lat, lon):
    url = f"https://www.google.com/maps/search/?api=1&query={lat},{lon}"
    print(" 開啟 Google Maps：", url)

    options = webdriver.ChromeOptions()
    options.add_argument("--lang=zh-TW")
    options.add_argument("user-agent=Mozilla/5.0")
    options.add_argument("--disable-blink-features=AutomationControlled")

    # ★★ 自動下載、管理、配置 driver ★★
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=options)

    driver.get(url)
    time.sleep(3)

    address_text = None

    # 嘗試抓 span.DkEaL（你截圖裡的）
    try:
        elem = WebDriverWait(driver, 8).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "span.DkEaL"))
        )
        address_text = elem.text.strip()

    except Exception:
        print("⚠ span.DkEaL 找不到，嘗試備援方案")
        try:
            elem = WebDriverWait(driver, 8).until(
                EC.presence_of_element_located(
                    (By.CSS_SELECTOR, "button[data-item-id='address'] div[aria-hidden='true']")
                )
            )
            address_text = elem.text.strip()
        except:
            print("❌ Google Maps 介面可能更新，無法抓取地址")

    driver.quit()
    return address_text


# ============================================
# 主程式：生成 + 讀 GPS + 查地址
# ============================================

if __name__ == "__main__":

    base = r"C:\GPS_test"
    os.makedirs(base, exist_ok=True)

    img_path = os.path.join(base, "gps_test.jpg")

    # 1) 建立 GPS 圖片
    create_gps_jpeg(img_path, lat=25.0416667, lon=121.5041667)

    # 2) 讀取 GPS
    lat, lon = read_gps_from_image(img_path)

    print(f"緯度：{lat}")
    print(f"經度：{lon}")
    print("\nGoogle Maps 連結：")
    print(f"https://maps.google.com/?q={lat},{lon}")

    # 3) 自動查地址
    address = get_address_from_google(lat, lon)

    print("\n 地址：", address if address else "（未找到）")
