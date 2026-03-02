import math

# 判斷事件是否在前方
def is_ahead_event(vehicle_heading, event_bearing, threshold=60):
    diff = abs(event_bearing - vehicle_heading)
    diff = min(diff, 360 - diff)
    return diff <= threshold

# 過濾前方事件
def filter_ahead_events(events, vehicle_heading):
    return [
        e for e in events
        if is_ahead_event(vehicle_heading, e.bearing)
    ]

# 車速影響
def calc_speed_score(speed):
    if speed.historical_speed == 0:
        return 0
    return max(0, 1 - speed.current_speed / speed.historical_speed)

# 事件影響
def calc_event_score(events):
    score = 0
    for e in events:
        type_weight = {
            "accident": 1.0,
            "construction": 0.8,
            "jam": 0.6
        }.get(e.type, 0)

        time_weight = max(0, 1 - e.minutes_ago / 10)
        dist_weight = max(0, 1 - e.distance / 500)

        score += type_weight * time_weight * dist_weight

    return min(score, 1)

# 壅塞總分（強化版）
def calc_congestion_score(speed, events, reports):
    s_speed = calc_speed_score(speed)
    s_event = calc_event_score(events)
    s_user = 1 - math.exp(-sum(r.confidence for r in reports))

    # 基礎分數
    base_score = 0.45*s_speed + 0.3*s_event + 0.25*s_user

    # 🔥 規則加強
    if s_event > 0.3:
        base_score += 0.2

    if len(reports) >= 3:
        base_score += 0.2

    return min(base_score, 1)
