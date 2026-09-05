import shutil
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from db.database import get_db
from db.models import Chunk, Session as SessionModel
from models.schemas import HealthResponse
from pipeline.transcriber import get_whisper_status
from pipeline.processor import get_bedrock_status
import config

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
async def health(db: AsyncSession = Depends(get_db)):
    usage = shutil.disk_usage(config.AUDIO_DIR)
    disk_free_gb = usage.free / 1024 ** 3

    queue_result = await db.execute(
        select(func.count()).select_from(Chunk).where(Chunk.status == "pending")
    )
    queue_depth = queue_result.scalar() or 0

    last_result = await db.execute(
        select(SessionModel.created_at)
        .where(SessionModel.status == "complete")
        .order_by(SessionModel.created_at.desc())
        .limit(1)
    )
    last_at = last_result.scalar()

    whisper_status = get_whisper_status()
    bedrock_status = get_bedrock_status()

    overall = "ok" if whisper_status != "down" and disk_free_gb > 1.0 else "degraded"

    return HealthResponse(
        status=overall,
        upload_queue_depth=queue_depth,
        whisper_worker_status=whisper_status,
        last_transcript_at=last_at.isoformat() if last_at else None,
        disk_free_gb=round(disk_free_gb, 2),
        bedrock_last_call_status=bedrock_status,
    )
