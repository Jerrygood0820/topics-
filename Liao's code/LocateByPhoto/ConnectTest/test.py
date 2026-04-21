from typing import Optional, List
from fastapi import FastAPI
from pydantic import BaseModel
import psycopg2
import psycopg2.extras

app = FastAPI()

def get_conn():
    return psycopg2.connect(
        dbname="taoyuantraffic",
        user="postgres",
        password="hanky931226",
        host="127.0.0.1",
        port="5432"
    )

class UserReportCreate(BaseModel):
    lat: float
    lon: float
    report_type: str
    confidence: float = 0.5
    road_segment_id: Optional[int] = None

class UserReportResponse(BaseModel):
    id: int
    lat: float
    lon: float
    report_type: str
    confidence: float
    road_segment_id: Optional[int]
    reported_at: str

@app.post("/user_report")
def create_user_report(data: UserReportCreate):
    conn = get_conn()
    cur = conn.cursor()

    sql = """
    INSERT INTO public.user_report
    (location, report_type, confidence, road_segment_id)
    VALUES (
        ST_SetSRID(ST_MakePoint(%s, %s), 4326)::geography,
        %s,
        %s,
        %s
    )
    RETURNING id, reported_at;
    """

    cur.execute(sql, (
        data.lon,
        data.lat,
        data.report_type,
        data.confidence,
        data.road_segment_id
    ))

    row = cur.fetchone()
    conn.commit()
    cur.close()
    conn.close()

    return {
        "message": "report saved",
        "id": row[0],
        "reported_at": row[1].isoformat()
    }

@app.get("/user_report", response_model=List[UserReportResponse])
def get_user_reports():
    conn = get_conn()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)

    sql = """
    SELECT
        id,
        ST_Y(location::geometry) AS lat,
        ST_X(location::geometry) AS lon,
        report_type,
        confidence,
        road_segment_id,
        reported_at
    FROM public.user_report
    ORDER BY reported_at DESC;
    """

    cur.execute(sql)
    rows = cur.fetchall()

    result = []
    for row in rows:
        result.append({
            "id": row["id"],
            "lat": row["lat"],
            "lon": row["lon"],
            "report_type": row["report_type"],
            "confidence": row["confidence"],
            "road_segment_id": row["road_segment_id"],
            "reported_at": row["reported_at"].isoformat()
        })

    cur.close()
    conn.close()
    return result