from datetime import datetime, timedelta
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, text
from db.database import get_db
from db.models import Session as SessionModel, Chunk
from models.schemas import TodayStatus
import json

router = APIRouter()


@router.get("/today/status", response_model=TodayStatus)
async def today_status(db: AsyncSession = Depends(get_db)):
    today = datetime.utcnow().strftime("%Y-%m-%d")

    sessions_result = await db.execute(
        select(SessionModel).where(SessionModel.started_at.like(f"{today}%"))
    )
    sessions = sessions_result.scalars().all()
    sessions_today = len(sessions)

    people_today = set()
    for s in sessions:
        for p in json.loads(s.people_json or "[]"):
            people_today.add(p)

    queue_result = await db.execute(
        select(func.count()).select_from(Chunk).where(Chunk.status == "pending")
    )
    queue_depth = queue_result.scalar() or 0

    last_chunk_result = await db.execute(
        select(Chunk.recorded_at).where(Chunk.status == "assembled").order_by(Chunk.recorded_at.desc()).limit(1)
    )
    last_chunk_at = last_chunk_result.scalar()
    recording = False
    if last_chunk_at:
        try:
            last_dt = datetime.fromisoformat(last_chunk_at.replace("Z", ""))
            recording = (datetime.utcnow() - last_dt) < timedelta(minutes=2)
        except Exception:
            pass

    last_transcript_result = await db.execute(
        select(SessionModel.created_at)
        .where(SessionModel.status == "complete")
        .order_by(SessionModel.created_at.desc())
        .limit(1)
    )
    last_transcript_at = last_transcript_result.scalar()

    return TodayStatus(
        recording=recording,
        sessions_today=sessions_today,
        people_today=len(people_today),
        queue_depth=queue_depth,
        last_transcript_at=last_transcript_at.isoformat() if last_transcript_at else None,
    )
