from models import SpeedInfo, Event, UserReport

def get_test_data():
    data = []

    # 1. 紅燈停等
    data.append({
        "name": "紅燈停等",
        "speed": SpeedInfo(5, 50),
        "events": [],
        "reports": [],
        "stop_duration": 40,
        "near_signal": True,
        "label": 0
    })

    # 2. 順暢
    data.append({
        "name": "順暢道路",
        "speed": SpeedInfo(50, 55),
        "events": [],
        "reports": [],
        "stop_duration": 0,
        "near_signal": False,
        "label": 0
    })

    # 3. 事故壅塞
    data.append({
        "name": "事故壅塞",
        "speed": SpeedInfo(10, 60),
        "events": [Event("accident", 50, 2, 10)],
        "reports": [],
        "stop_duration": 20,
        "near_signal": False,
        "label": 1
    })

    # 4. 施工壅塞
    data.append({
        "name": "施工壅塞",
        "speed": SpeedInfo(20, 60),
        "events": [Event("construction", 100, 5, 20)],
        "reports": [],
        "stop_duration": 10,
        "near_signal": False,
        "label": 1
    })

    # 5. 多人回報
    reports = []
    for i in range(5):
        reports.append(UserReport(str(i), 30, 1))

    data.append({
        "name": "多人回報壅塞",
        "speed": SpeedInfo(45, 50),
        "events": [],
        "reports": reports,
        "stop_duration": 5,
        "near_signal": False,
        "label": 1
    })

    return data
