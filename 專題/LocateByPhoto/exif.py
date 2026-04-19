from PIL import Image
from PIL.ExifTags import TAGS, GPSTAGS
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

def get_exif(img_path):
    img = Image.open(img_path)
    exif_data = img._getexif()
    if not exif_data:
        return None

    exif = {}
    for tag_id, value in exif_data.items():
        tag = TAGS.get(tag_id, tag_id)

        if tag == "GPSInfo":
            gps_data = {}
            for key in value:
                gps_tag = GPSTAGS.get(key, key)
                gps_data[gps_tag] = value[key]
            exif[tag] = gps_data
        else:
            exif[tag] = value
    return exif

def rational_to_float(x):
    """將 IFD Rational 或 tuple 轉成 float"""
    try:
        return float(x)
    except:
        return x[0] / x[1]

def dms_to_dd(dms, ref):
    """支援 IFDRational、tuple、list"""
    degrees = rational_to_float(dms[0])
    minutes = rational_to_float(dms[1])
    seconds = rational_to_float(dms[2])

    dd = degrees + minutes/60 + seconds/3600
    if ref in ['S', 'W']:
        dd = -dd
    return dd


def get_gps_location(exif):
    if "GPSInfo" not in exif:
        return None
    
    gps = exif["GPSInfo"]

    # 緯度
    lat = dms_to_dd(gps["GPSLatitude"], gps["GPSLatitudeRef"])
    # 經度
    lon = dms_to_dd(gps["GPSLongitude"], gps["GPSLongitudeRef"])

    return lat, lon


base = os.path.dirname(__file__)
img_path = os.path.join(base, "gps_test.jpg")

exif = get_exif(img_path)
print(exif)


if not exif:
    print(" 無 EXIF 資料（可能已被清除）")
elif "GPSInfo" not in exif:
    print(" EXIF 中沒有 GPS 資訊，無法判斷地點")
else:
    lat, lon = get_gps_location(exif)
    print(" 緯度:", lat)
    print(" 經度:", lon)

    print("\n Google Maps 連結：")
    print(f"https://maps.google.com/?q={lat},{lon}")
